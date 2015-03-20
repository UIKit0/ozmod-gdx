/*
OZMod - Java Sound Library
Copyright (C) 2012 by Igor Kravtchenko

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

Contact the author: igor@tsarevitch.org
 */

package ozmod;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Arrays;

import ozmod.SeekableBytes.Endian;

/**
 * A Class to replay S3M file.
 */
public class S3MPlayer extends OZModPlayer {
	protected static class Instru {

		AudioData audio = new AudioData();
		int C2Speed;
		int defaultVolume;
		int disk;
		byte DOSname[] = new byte[0xc];
		int endLoop;
		int flags;
		byte instruName[] = new byte[0x1c];
		int packType;
		int sampleDataLen;
		int sampleDataOffset;
		int startLoop;

		int type;
	}

	protected static class Note {
		int command;
		int commandParam;
		int note;
		int numInstru;
		int octave;
		int vol;
	}
	protected static class Pattern {
		Row rows[] = new Row[64];
	}
	protected static class Row {
		Note notes[];
	}
	protected class Voice {

		protected int arpeggioCount_, arp1_, arp2_;

		protected boolean bFineVibrato_;

		protected boolean bGotArpeggio_;

		protected boolean bGotNoteCut_;

		protected boolean bGotRetrigNote_;

		protected boolean bGotTremolo_;

		protected boolean bGotTremor_;

		protected boolean bGotVibrato_;
		protected boolean bNeedToBePlayed_;

		protected boolean bNeedToStopSamplePlaying_;

		protected boolean bNoteIsCutted_;
		protected Instru instruPlaying_;
		protected Instru instruToPlay_;
		protected int lastDCommandParam_;
		protected int note_, command_, commandParam_, extraCommand_;
		protected int noteCutDelay_;

		protected int numVoice_;
		protected int panning_;

		protected int period_, dstPeriod_, periodBAK_;

		protected int portamentoSpeed_;

		protected int portaSpeed_;
		protected int samplePosJump_;
		protected Channel sndchan_ = new Channel();

		protected int tickBeforeSample_;
		protected int tickForRetrigNote_ = -1;
		protected int tremoloCounter_;
		protected int tremoloForm_;
		protected int tremoloSpeed_, tremoloProf_;

		protected int tremorCounter_;
		protected int tremorValue_;
		protected int vibratoCounter_;
		protected int vibratoForm_;

		protected int vibratoSpeed_, vibratoProf_;
		protected int volFadeOutForRetrig_;
		protected int volume_, volumeBAK_;

		Voice() {
		}
		void evalue_DCommand(int commandParam) {
			if (commandParam != 0)
				lastDCommandParam_ = commandParam;

			int actuCommandParam = lastDCommandParam_;

			int x = actuCommandParam >> 4;
			int y = actuCommandParam & 0xf;

			if (y == 0 || (y == 0x0f && x != 0)) {
				// VOL SLIDE UP
				if (y == 0) {
					// normal
					command_ = COMMAND_VOLSLIDEUP;
				} else {
					// fine vslide
					volume_ += x;
				}
			} else if (x == 0 || (x == 0x0f && y != 0)) {
				// VOL SLIDE DOWN
				if (x == 0) {
					// normal
					command_ = COMMAND_VOLSLIDEDOWN;
				} else {
					// fine vslide
					volume_ -= y;
				}
			}
		}

		void portamentoToNote() {
			if (period_ < dstPeriod_) {
				period_ += portamentoSpeed_;
				if (period_ > dstPeriod_)
					period_ = dstPeriod_;
			} else if (period_ > dstPeriod_) {
				period_ -= portamentoSpeed_;
				if (period_ < dstPeriod_)
					period_ = dstPeriod_;
			}
		}
		void soundUpdate() {
			int i;
			Instru instru;
			int freq;
			float Vol, Pan;

			if (instruPlaying_ == null)
				return;

			freq = 14317056 / period_;

			if (volume_ < 0)
				volume_ = 0;
			if (volume_ > 64)
				volume_ = 64;

			if (bNoteIsCutted_ == false)
				Vol = (volume_ / 64.0f) * globalVolume_;
			else
				Vol = 0;

			if (Vol < 0.0f)
				Vol = 0.0f;
			else if (Vol > 1.0f)
				Vol = 1.0f;

			Pan = (panning_ - 128) / 128.0f;

			if (bGotRetrigNote_ == true) {
				if ((tick_ - 1) % tickForRetrigNote_ == tickForRetrigNote_ - 1) {
					bNeedToBePlayed_ = true;
					volume_ += g_RetrigFadeOutTable[volFadeOutForRetrig_];
				}
			}
			if (bGotNoteCut_ == true) {
				if ((tick_ - 2) == noteCutDelay_)
					bNoteIsCutted_ = true;
			}

			if (bNeedToBePlayed_ == false) {
				sndchan_.frequency = freq;
				sndchan_.step = freq / (float) (frequency_);
				sndchan_.setPan(Pan);
				sndchan_.vol = Vol;
				return;
			}

			int StartPos;
			if (command_ == COMMAND_SAMPLEOFFSET) {
				command_ = COMMAND_NONE;
				StartPos = samplePosJump_;
			} else
				StartPos = 0;

			chansList_.removeChannel(sndchan_);
			// sndchan_.stop();

			if (tick_ >= tickBeforeSample_) {
				sndchan_.frequency = freq;
				sndchan_.step = freq / (float) (frequency_);
				sndchan_.vol = Vol;
				sndchan_.setPan(Pan);
				sndchan_.pos = (float) StartPos;
				sndchan_.audio = instruPlaying_.audio;
				chansList_.addChannel(sndchan_);

				bNeedToBePlayed_ = false;
				tickBeforeSample_ = 0;
			}
		}

		void tremolo() {
			int tremoloSeek;
			int volumeOffset;

			tremoloSeek = (tremoloCounter_ >> 2) & 0x3f;

			switch (tremoloForm_) {
			default:
				volumeOffset = TrackerConstant.vibratoTable[tremoloSeek];
				break;
			}

			volumeOffset *= tremoloProf_;
			volumeOffset >>= 6;
			volume_ = volumeBAK_ + volumeOffset;

			tremoloCounter_ += tremoloSpeed_;
		}
		void updateSoundWithEffect() {
			int on, off, pos;

			switch (command_) {
			case COMMAND_VOLSLIDEDOWN:
				volume_ -= lastDCommandParam_ & 0x0f;
				break;

			case COMMAND_VOLSLIDEUP:
				volume_ += lastDCommandParam_ >> 4;
				break;

			case COMMAND_SLIDEDOWN:
				period_ += portaSpeed_ * 4;
				break;

			case COMMAND_SLIDEUP:
				period_ -= portaSpeed_ * 4;
				break;

			case COMMAND_TONEPORTAMENTO:
				portamentoToNote();
				break;

			case COMMAND_VIBRATO:
				vibrato(bFineVibrato_);
				break;

			case COMMAND_TREMOR:
				on = tremorValue_ >> 4;
				on++;
				off = tremorValue_ & 0xf;
				off++;
				pos = tremorCounter_ % (on + off);
				if (pos < on)
					volume_ = volumeBAK_;
				else
					volume_ = 0;
				tremorCounter_++;
				break;

			case COMMAND_ARPEGGIO:
				switch (arpeggioCount_ % 3) {
				case 0:
					period_ = getST3period(note_ + arp2_,
							instruPlaying_.C2Speed);
					break;
				case 1:
					period_ = getST3period(note_ + arp1_,
							instruPlaying_.C2Speed);
					break;
				case 2:
					period_ = getST3period(note_, instruPlaying_.C2Speed);
					break;
				}
				arpeggioCount_++;
				break;

			case COMMAND_TREMOLO:
				tremolo();
				break;
			}

			switch (extraCommand_) {
			case EXTRACOMMAND_DUALK:
				vibrato(false);
				break;

			case EXTRACOMMAND_DUALL:
				portamentoToNote();
				break;
			}
		}
		void vibrato(boolean _bFine) {
			int vibSeek;
			int periodOffset;

			vibSeek = (vibratoCounter_ >> 2) & 0x3f;

			switch (vibratoForm_) {
			default:
				periodOffset = TrackerConstant.vibratoTable[vibSeek];
				break;
			}

			periodOffset *= vibratoProf_;
			periodOffset >>= 7;
			if (_bFine == false)
				periodOffset <<= 2;
			period_ = periodBAK_ + periodOffset;
			vibratoCounter_ += vibratoSpeed_;
		}
	}
	protected static final int COMMAND_ARPEGGIO = 9;
	protected static final int COMMAND_NONE = 0;
	protected static final int COMMAND_RETRIG = 11;
	protected static final int COMMAND_SAMPLEOFFSET = 10;
	protected static final int COMMAND_SLIDEDOWN = 3;
	protected static final int COMMAND_SLIDEUP = 4;
	protected static final int COMMAND_TONEPORTAMENTO = 5;
	protected static final int COMMAND_TREMOLO = 8;
	protected static final int COMMAND_TREMOR = 7;
	protected static final int COMMAND_VIBRATO = 6;
	protected static final int COMMAND_VOLSLIDEDOWN = 1;
	protected static final int COMMAND_VOLSLIDEUP = 2;
	protected static final int EXTRACOMMAND_DUALK = 1;
	protected static final int EXTRACOMMAND_DUALL = 2;
	protected static final int EXTRACOMMAND_NONE = 0;;
	protected static final int g_Period[] = { 1712, 1616, 1524, 1440, 1356,
			1280, 1208, 1140, 1076, 1016, 960, 907 };;
	protected static final int g_RetrigFadeOutTable[] = { 0, -1, -2, -4, -8,
			-16, -32, -64, 0, 1, 2, 4, 8, 16, 32, 64 };;
	protected static final int MAX_NB_CHANNELS = 32;
	protected boolean bGotPatternLoop_;
	protected int chanRemap_[] = new int[MAX_NB_CHANNELS];
	protected int fileFlags_;
	protected int fileType_;
	protected float globalVolume_ = 1.0f;
	protected int ID_;
	protected Instru instrus_[];
	protected int listLen_;
	protected float localVolume_;
	protected int masterMultiplier_;
	protected int nbInstrus_;
	protected int nbPatterns_;
	protected int numListPattern_[];
	protected int panChannel_[] = new int[32];
	protected int patternDelay_ = -1;
	protected int patternLoopLeft_;
	protected int patternPosLoop_;
	protected Pattern patterns_[];
	protected int posChanson_ = 0;
	protected int posInPattern_;
	protected int realNbChannels_;
	protected float realVolume_;
	protected byte songName_[] = new byte[0x1c];
	protected int specChannel_[] = new int[32];
	protected int startSpeed_;
	protected int startTempo_;
	protected int tempo_;
	protected Voice voices_[];
	protected int volumeMaster_;
	public S3MPlayer(IAudioDevice audioDevice) {
		super(audioDevice);
	}

	@Override
	public String getSongName() {
		return new String(songName_, Charset.forName("UTF-8"));
	}
	
	protected void dispatchNotes() {
		int note, numInstru, volume, command, commandParam;
		int actuCommandParam;
		int com, par;
		int numVoice;

		int newSpeed = 0;
		int newTempo = 0;

		// again:;
		int actuPattern = numListPattern_[posChanson_];
		/*
		 * if (actuPattern >= nbPatterns_) { posChanson_++; if (posChanson_ ==
		 * listLen_ || numListPattern_[ posChanson_ ] == 0xff) {
		 * sendEndNotify(notifiesList_, nbNotifies_); if (flags_ == FLAG_LOOP) {
		 * posChanson_ = 0; for (int i = 0; i < realNbChannels_; i++) {
		 * voices_[i].sndchan_.stop(); } } else { stop(true, false); } return; }
		 * else goto again; }
		 */

		boolean bGotPatternJump = false;
		boolean bGotPatternBreak = false;
		int whereToJump = 0;
		int whereToBreak = 0;

		Row actuRow = patterns_[actuPattern].rows[posInPattern_];

		for (numVoice = 0; numVoice < realNbChannels_; numVoice++) {
			Voice voice = voices_[numVoice];

			// if (numVoice != 2)
			// continue;

			note = actuRow.notes[numVoice].note;
			volume = actuRow.notes[numVoice].vol;
			numInstru = actuRow.notes[numVoice].numInstru;
			command = actuRow.notes[numVoice].command;
			commandParam = actuRow.notes[numVoice].commandParam;

			boolean AllowToUpdateNote = true;

			if (voice.command_ != COMMAND_VIBRATO
					&& voice.extraCommand_ != EXTRACOMMAND_DUALK
					&& voice.bGotVibrato_ == true) {
				voice.period_ = voice.periodBAK_;
				voice.bGotVibrato_ = false;
			} else if (voice.command_ != COMMAND_TREMOR
					&& voice.bGotTremor_ == true) {
				voice.volume_ = voice.volumeBAK_;
				voice.bGotTremor_ = false;
			} else if (voice.command_ != COMMAND_TREMOLO
					&& voice.bGotTremolo_ == true) {
				voice.volume_ = voice.volumeBAK_;
				voice.bGotTremolo_ = false;
			} else if (voice.command_ != COMMAND_ARPEGGIO
					&& voice.bGotArpeggio_ == true) {
				voice.period_ = voice.periodBAK_;
				voice.bGotArpeggio_ = false;
			}

			if (voice.instruPlaying_ != null) {
				if (command == ('G' - 0x40))
					AllowToUpdateNote = false;
			}

			if (numInstru > 0) {
				if (numInstru <= nbInstrus_)
					voice.instruToPlay_ = instrus_[numInstru - 1];
				else {
					chansList_.removeChannel(voice.sndchan_);
					voice.instruPlaying_ = null;
					voice.instruToPlay_ = null;
				}

				if (voice.instruToPlay_ != null) {
					voice.volume_ = voice.instruToPlay_.defaultVolume;
					voice.volumeBAK_ = voice.volumeBAK_;
				}
				voice.vibratoCounter_ = 0;
				voice.tremorCounter_ = 0;
			}

			if (note > 0 && note < 97 && voice.instruToPlay_ != null) {
				int Period;

				Period = getST3period(note, voice.instruToPlay_.C2Speed);
				voice.periodBAK_ = Period;
				voice.note_ = note;

				if (AllowToUpdateNote == true) {
					voice.instruPlaying_ = voice.instruToPlay_;
					voice.period_ = voice.dstPeriod_ = Period;
					voice.bNeedToBePlayed_ = true;
					voice.bNoteIsCutted_ = false;
				}
			} else if (note == 194) {
				voice.bNoteIsCutted_ = true;
			}

			if (volume != 0) {
				voice.volume_ = volume - 1;
				voice.volumeBAK_ = voice.volume_;
			}

			voice.command_ = COMMAND_NONE;
			voice.extraCommand_ = EXTRACOMMAND_NONE;
			voice.bGotRetrigNote_ = false;
			voice.bGotNoteCut_ = false;
			voice.bFineVibrato_ = false;

			switch (command) {
			case 'A' - 0x40:
				// ===========
				// SET SPEED
				// ===========

				newSpeed = commandParam;
				break;

			case 'B' - 0x40:
				// ===============
				// JUMP TO ORDER
				// ===============

				bGotPatternJump = true;
				whereToJump = commandParam;
				break;

			case 'C' - 0x40:
				// ===============
				// PATTERN BREAK
				// ===============

				bGotPatternBreak = true;
				whereToBreak = (commandParam >> 4) * 10 + (commandParam & 0x0f);
				break;

			case 'D' - 0x40:
				// ==========================
				// NORMAL/FINE VOLUME SLIDE
				// ==========================

				voice.evalue_DCommand(commandParam);
				break;

			case 'E' - 0x40:
				// ============
				// SLIDE DOWN
				// ============

				if (commandParam != 0)
					voice.portaSpeed_ = commandParam;

				actuCommandParam = voice.portaSpeed_;
				if ((actuCommandParam >> 4) == 0x0e) {
					// extra fine slide down
					voice.period_ += actuCommandParam & 0xf;
				} else if ((actuCommandParam >> 4) == 0x0f) {
					// fine slide down
					voice.period_ += (actuCommandParam & 0xf) * 4;
				} else {
					// slide down
					voice.command_ = COMMAND_SLIDEDOWN;
				}
				break;

			case 'F' - 0x40:
				// ==========
				// SLIDE UP
				// ==========

				if (commandParam != 0)
					voice.portaSpeed_ = commandParam;

				actuCommandParam = voice.portaSpeed_;
				if ((actuCommandParam >> 4) == 0x0e) {
					// extra fine slide up
					voice.period_ -= actuCommandParam & 0xf;
				} else if ((actuCommandParam >> 4) == 0x0f) {
					// fine slide up
					voice.period_ -= (actuCommandParam & 0xf);
				} else {
					// slide up
					voice.command_ = COMMAND_SLIDEUP;
				}
				break;

			case 'G' - 0x40:
				// ====================
				// PORTAMENTO TO NOTE
				// ====================

				if (note > 0 && note < 97 && voice.instruPlaying_ != null) {
					voice.dstPeriod_ = getST3period(note,
							voice.instruPlaying_.C2Speed);
				}

				if (commandParam != 0)
					voice.portamentoSpeed_ = commandParam * 4;

				voice.command_ = COMMAND_TONEPORTAMENTO;

				break;

			case 'U' - 0x40:
				voice.bFineVibrato_ = true;
				// no break, continuy ..

			case 'H' - 0x40:
				// =========
				// VIBRATO
				// =========

				voice.command_ = COMMAND_VIBRATO;
				if ((commandParam >> 4) != 0)
					voice.vibratoSpeed_ = (commandParam >> 4) * 4;
				if ((commandParam & 0x0f) != 0)
					voice.vibratoProf_ = commandParam & 0x0f;
				break;

			case 'I' - 0x40:
				// ========
				// TREMOR
				// ========

				voice.command_ = COMMAND_TREMOR;

				if (commandParam != 0)
					voice.tremorValue_ = commandParam;
				break;

			case 'J' - 0x40:
				// ==========
				// ARPEGGIO
				// ==========

				voice.command_ = COMMAND_ARPEGGIO;

				if ((commandParam & 0xf) != 0)
					voice.arp1_ = commandParam & 0xf;
				if ((commandParam >> 4) != 0)
					voice.arp2_ = commandParam >> 4;

				voice.arpeggioCount_ = 0;
				break;

			case 'K' - 0x40:
				// ===========================
				// DUAL COMMAND: H00 and Dxy
				// ===========================

				voice.extraCommand_ = EXTRACOMMAND_DUALK;
				voice.evalue_DCommand(commandParam);
				break;

			case 'L' - 0x40:
				// ===========================
				// DUAL COMMAND: G00 and Dxy
				// ===========================

				voice.extraCommand_ = EXTRACOMMAND_DUALL;
				voice.evalue_DCommand(commandParam);
				break;

			case 'O' - 0x40:
				voice.command_ = COMMAND_SAMPLEOFFSET;
				if (commandParam != 0)
					voice.samplePosJump_ = commandParam << 8;
				break;

			case 'Q' - 0x40:
				voice.command_ = COMMAND_RETRIG;
				if (commandParam != 0) {
					voice.tickForRetrigNote_ = commandParam & 0xf;
					voice.volFadeOutForRetrig_ = commandParam >> 4;
				}

				voice.bGotRetrigNote_ = true;
				break;

			case 'R' - 0x40:
				// =========
				// TREMOLO
				// =========

				voice.command_ = COMMAND_TREMOLO;

				if ((commandParam >> 4) != 0)
					voice.tremoloSpeed_ = (commandParam >> 4) * 4;
				if ((commandParam & 0x0f) != 0)
					voice.tremoloProf_ = commandParam & 0x0f;
				break;

			case 'S' - 0x40:
				// ===============
				// MISCELLANEOUS
				// ===============

				com = commandParam >> 4;
				par = commandParam & 0xf;

				switch (com) {
				case 0x8:
					// channel pan posistion
					voice.panning_ = par << 4;
					break;

				case 0xb:
					// pattern loop
					if (par == 0)
						patternPosLoop_ = posInPattern_;
					else {
						if (bGotPatternLoop_ == false) {
							bGotPatternLoop_ = true;
							patternLoopLeft_ = par;
						}
						patternLoopLeft_--;
						if (patternLoopLeft_ >= 0) {
							posInPattern_ = patternPosLoop_ - 1;
						} else
							bGotPatternLoop_ = false;
					}
					break;

				case 0xc:
					// note cut
					voice.noteCutDelay_ = par;
					voice.bGotNoteCut_ = true;
					break;

				case 0xd:
					// note delay
					voice.tickBeforeSample_ = par + 1;
					break;

				case 0xe:
					// pattern delay
					if (patternDelay_ < 0)
						patternDelay_ = par;
					break;
				}
				break;

			case 'T' - 0x40:
				// ===========
				// SET TEMPO
				// ===========

				newTempo = commandParam;
				break;

			case 'V' - 0x40:
				// ===================
				// SET GLOBAL VOLUME
				// ===================

				globalVolume_ = commandParam / 64.f;
				break;

			case 'X' - 0x40:
				// =============
				// SET PANNING
				// =============

				voice.panning_ = (commandParam & 0x7f) << 1;
				break;

			}

			if ((voice.command_ == COMMAND_VIBRATO || voice.extraCommand_ == EXTRACOMMAND_DUALK)
					&& voice.bGotVibrato_ == false) {
				voice.periodBAK_ = voice.period_;
				voice.bGotVibrato_ = true;
			} else if (voice.command_ == COMMAND_TREMOR
					&& voice.bGotTremor_ == false) {
				voice.volumeBAK_ = voice.volume_;
				voice.bGotTremor_ = true;
			} else if (voice.command_ == COMMAND_TREMOLO
					&& voice.bGotTremolo_ == false) {
				voice.volumeBAK_ = voice.volume_;
				voice.bGotTremolo_ = true;
			} else if (voice.command_ == COMMAND_ARPEGGIO
					&& voice.bGotArpeggio_ == false) {
				voice.periodBAK_ = voice.period_;
				voice.bGotArpeggio_ = true;
			}
		}

		if (newSpeed != 0)
			speed_ = newSpeed;

		if (newTempo != 0)
			tempo_ = newTempo;

		posInPattern_++;

		if (posInPattern_ == 64 || bGotPatternJump == true
				|| bGotPatternBreak == true) {
			posInPattern_ = whereToBreak;
			if (bGotPatternJump == true)
				posChanson_ = whereToJump;
			else
				posChanson_++;

			if (posChanson_ == listLen_ || numListPattern_[posChanson_] == 0xff) {

				if (loopable_ == true)
					posChanson_ = 0;
				else
					running_ = false;
			}
		}
	}
	protected void finalize() {
		running_ = false;
	}
	/**
	 * Gets the current reading position of the song.
	 * 
	 * @return The current position.
	 */
	public int getCurrentPos() {
		return posInPattern_;
	}

	/**
	 * Gets the current reading row of the song.
	 * 
	 * @return The current row.
	 */
	public int getCurrentRow() {
		return posChanson_;
	}

	/**
	 * Gets the internal buffer used to mix the samples together. It can be used
	 * for instance to analyze the wave, apply effect or whatever.
	 * 
	 * @return The internal mix buffer.
	 */
	public byte[] getMixBuffer() {
		return pcm_;
	}
	protected int getST3period(int note, int c2speed) {
		int n = note % 12;
		int o = note / 12;

		int h = (8363 * 16 * g_Period[n]) >> o;

		if (c2speed == 0)
			return 1;
		else
			return h / c2speed;
	}
	/**
	 * Tells if the S3M is loopable or not.
	 * 
	 * @return true if loopable, false otherwhise.
	 */
	public boolean isLoopable() {
		return loopable_;
	}
	/**
	 * Loads the S3M.
	 * 
	 * @param _input
	 *            An instance to a PipeIn Class to read data from disk or URL.
	 * @return NOERR if no error occured.
	 */
	@Override
	public void load(byte[] bytes) {

		SeekableBytes buffer = new SeekableBytes(bytes, Endian.LITTLEENDIAN);

		byte tmp[] = new byte[4];

		buffer.readFully(songName_);
		ID_ = buffer.readByte();
		fileType_ = buffer.readByte();
		buffer.forward(2);

		listLen_ = buffer.readUShort();
		nbInstrus_ = buffer.readUShort();
		nbPatterns_ = buffer.readUShort();
		fileFlags_ = buffer.readUShort();
		buffer.forward(2); // version info
		buffer.forward(2); // format version
		buffer.read(tmp, 0, 4);
		String format = new String(tmp).substring(0, 4);
		if (format.compareTo("SCRM") != 0)
			throw new OZModRuntimeError(OZMod.ERR.BADFORMAT);

		int initialPanID;

		volumeMaster_ = buffer.readUByte();
		startSpeed_ = buffer.readUByte();
		startTempo_ = buffer.readUByte();
		masterMultiplier_ = buffer.readUByte();
		buffer.forward(1);
		initialPanID = buffer.readUByte();
		buffer.forward(10);
		for (int i = 0; i < 32; i++)
			specChannel_[i] = buffer.readUByte();

		for (int i = 0; i < MAX_NB_CHANNELS; i++)
			chanRemap_[i] = 255;

		int nbChannels = 0;
		for (int i = 0; i < 32; i++) {
			int specChannel = specChannel_[i];
			if (specChannel < 16) {
				chanRemap_[i] = nbChannels;
				nbChannels++;
			}
		}
		realNbChannels_ = nbChannels;

		numListPattern_ = new int[listLen_];
		for (int i = 0; i < listLen_; i++) {
			int num = buffer.readUByte();
			numListPattern_[i] = num;
		}

		// Read instru and related info
		instrus_ = new Instru[nbInstrus_];
		int actuFilePos = buffer.tell();
		for (int numInstru = 0; numInstru < nbInstrus_; numInstru++) {
			Instru instru = new Instru();
			instrus_[numInstru] = instru;

			int offsetInstru;
			buffer.seek(actuFilePos);
			actuFilePos += 2;
			offsetInstru = buffer.readUShort();
			offsetInstru <<= 4;
			buffer.seek(offsetInstru);

			instru.type = buffer.readUByte();
			buffer.readFully(instru.DOSname);
			byte doff[] = new byte[3];
			buffer.read(doff, 0, 3);
			int b1 = doff[0];
			int b2 = (doff[1] & 0xff) << 8;
			int b3 = (doff[2] & 0xff) << 16;
			instru.sampleDataOffset = b1 | b2 | b3;
			instru.sampleDataLen = buffer.readInt();
			instru.startLoop = buffer.readInt();
			instru.endLoop = buffer.readInt();
			instru.defaultVolume = buffer.readUByte();
			instru.disk = buffer.readUByte();
			instru.packType = buffer.readUByte();
			instru.flags = buffer.readUByte();
			instru.C2Speed = buffer.readInt();
			buffer.forward(4); // non-used
			buffer.forward(2); // gravis memory position
			buffer.forward(6); // used for ?
			buffer.readFully(instru.instruName);

			int strID;
			strID = buffer.readInt();

			int sizeForSample = instru.sampleDataLen;
			if (sizeForSample == 0)
				continue;

			int nbC = 1;
			int nbBits = 8;

			if ((instru.flags & 2) != 0) {
				nbC = 2;
				sizeForSample *= 2;
			}
			if ((instru.flags & 4) != 0) {
				nbBits = 16;
				sizeForSample *= 2;
			}

			int startLoop = instru.startLoop;
			int endLoop = instru.endLoop;

			int off = instru.sampleDataOffset >> 4;
			buffer.seek(off);

			byte pcm[] = new byte[sizeForSample];
			buffer.readFully(pcm);

			if (nbBits == 8) {
				for (int i = 0; i < sizeForSample; i++)
					pcm[i] -= 128;

				if (nbC == 2) {
					// left and right are entrelaced
					byte tmpb[] = new byte[pcm.length];
					int j = 0;
					for (int i = 0; i < instru.sampleDataLen; i++) {
						byte left = pcm[i];
						byte right = pcm[i + instru.sampleDataLen];
						tmpb[j++] = left;
						tmpb[j++] = right;
					}
					pcm = tmpb;
				}
			} else {
				int nbsamp = instru.sampleDataLen * nbC;
				for (int i = 0; i < nbsamp; i++) {
					short b = (short) (pcm[i * 2 + 0] & 0xff);
					b |= (short) (pcm[i * 2 + 1] << 8);
					b -= 32768;
					pcm[i * 2 + 0] = (byte) (b & 0xff);
					pcm[i * 2 + 1] = (byte) (b >> 8);
				}

				if (nbC == 2) {
					// left and right are entrelaced
					byte tmpb[] = new byte[pcm.length];
					int j = 0;
					for (int i = 0; i < instru.sampleDataLen; i++) {
						byte left0 = pcm[i * 2];
						byte left1 = pcm[i * 2 + 1];

						byte right0 = pcm[i * 2 + instru.sampleDataLen];
						byte right1 = pcm[i * 2 + 1 + instru.sampleDataLen * 2];

						tmpb[j++] = left0;
						tmpb[j++] = left1;
						tmpb[j++] = right0;
						tmpb[j++] = right1;
					}
					pcm = tmpb;
				}
			}

			if ((instru.flags & 1) == 0)
				instru.audio.make(pcm, nbBits, nbC);
			else
				instru.audio.make(pcm, nbBits, nbC, startLoop, endLoop,
						AudioData.LOOP_FORWARD);
		}

		// Read pattern
		patterns_ = new Pattern[nbPatterns_];
		for (int numPattern = 0; numPattern < nbPatterns_; numPattern++) {

			Pattern pattern = new Pattern();
			patterns_[numPattern] = pattern;

			int offsetPattern = 0;
			buffer.seek(actuFilePos);
			actuFilePos += 2;

			offsetPattern = buffer.readUShort();
			offsetPattern <<= 4;
			buffer.seek(offsetPattern + 2);

			Note bidonNote = new Note();
			Note actuNote;

			int numRow = 0;
			while (numRow < 64) {
				Row row = new Row();
				pattern.rows[numRow] = row;
				row.notes = new Note[nbChannels];
				for (int i = 0; i < nbChannels; i++)
					row.notes[i] = new Note();

				while (true) {
					int byt = 0;
					byt = buffer.readUByte();
					if (byt == 0)
						break;

					int numChan = byt & 31;
					int realNumChan = chanRemap_[numChan];

					if (realNumChan < 16)
						actuNote = row.notes[realNumChan];
					else
						actuNote = bidonNote;

					if ((byt & 32) != 0) {
						int note;
						note = buffer.readUByte();
						actuNote.numInstru = buffer.readUByte();
						actuNote.note = ((note >> 4) * 12) + (note & 0xf);
					}

					if ((byt & 64) != 0) {
						actuNote.vol = buffer.readUByte();
						actuNote.vol++;
					}

					if ((byt & 128) != 0) {
						actuNote.command = buffer.readUByte();
						actuNote.commandParam = buffer.readUByte();
					}
				}
				numRow++;
			}
		}

		voices_ = new Voice[realNbChannels_];
		for (int i = 0; i < realNbChannels_; i++) {
			Voice voice = new Voice();
			voices_[i] = voice;

			voice.numVoice_ = i;
			if ((i & 1) != 0)
				voice.panning_ = 192;
			else
				voice.panning_ = 64;
		}

		if (initialPanID == 252) {
			buffer.seek(actuFilePos);
			for (int i = 0; i < 32; i++)
				panChannel_[i] = buffer.readUByte();

			int j = 0;
			for (int i = 0; i < 32; i++) {
				if (specChannel_[i] < 16 && (panChannel_[i] & 0x20) != 0) {
					int pan = panChannel_[i] & 0xf;
					voices_[j++].panning_ = (short) (pan << 4);
				}
			}
		}

		speed_ = startSpeed_;
		tempo_ = startTempo_;

		return;
	}
	@Override
	protected void mixSample(int _time) {
		int nbsamp = frequency_ / (1000 / _time);
		Arrays.fill(pcm_, (byte) 0);
		chansList_.mix(nbsamp, pcm_);
		ByteBuffer.wrap(pcm_).order(ByteOrder.BIG_ENDIAN).asShortBuffer()
				.get(pcms_, 0, nbsamp * 2);
		pcmAudio.writeSamples(pcms_, 0, nbsamp * 2);
	}
	protected void oneShot(int _timer) {
		if (tick_ == speed_)
			tick_ = 0;
		tick_++;

		if (tick_ == 1) {
			patternDelay_--;
			if (patternDelay_ < 0)
				dispatchNotes();
		} else {
			for (int i = 0; i < realNbChannels_; i++)
				voices_[i].updateSoundWithEffect();
		}

		for (int i = 0; i < realNbChannels_; i++)
			voices_[i].soundUpdate();

		mixSample(_timer);
	}

	/**
	 * Starts to play the S3M. The time latency between a note is read and then
	 * heard is approximatively of 100ms. If the S3M is not loopable and finish,
	 * you cannot restart it by invoking again this method.
	 */
	@Override
	public void play() {
		if (isAlive() == true || done_ == true)
			return;

		running_ = true;

		start();
	}
	/**
	 * Never call this method directly. Use play() instead.
	 */
	@Override
	public void run() {
		frequency_ = 44100;

		int soundBufferLen = frequency_ * 4;
		pcm_ = new byte[soundBufferLen];
		pcms_ = new short[pcm_.length / 2];

		long cumulTime = 0;
		while (running_) {

			float timerRate = 1000.0f / (tempo_ * 0.4f);
			int intTimerRate = (int) Math.floor(timerRate);
			long since = timer_.getDelta();
			if (paused) {
				doSleep(100);
				continue;
			}
			cumulTime += since;

			if(cumulTime >= intTimerRate) {
				cumulTime -= intTimerRate;
				oneShot(intTimerRate);
			}
			doSleep((intTimerRate - cumulTime) / 2);
			totalTime += since;
			if (maxPlayTime>0 && totalTime>maxPlayTime) {
				done();
			}
		}
		done();
	}

	/**
	 * Sets the S3M loopable or not. The method can be called at any time if the
	 * song is still playing.
	 * 
	 * @param _b
	 *            true to loop the song, false otherwhise.
	 */
	public void setLoopable(boolean _b) {
		loopable_ = _b;
	}
	
	@Override
	public void setVolume(float _vol) {
		this.globalVolume_=_vol;		
	}
	@Override
	public float getVolume() {
		return this.globalVolume_;
	}
}
