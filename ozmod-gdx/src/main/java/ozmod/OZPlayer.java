package ozmod;

import ozmod.OZMod.ERR;
import ozmod.OZModPlayer.IAudioDevice;
import ozmod.SeekableBytes.Endian;

public class OZPlayer {
	private static final int SCRM_SKIP = 44;
	public static enum ModType {
		IT, MOD, S3M, XM;
	}
	public static OZModPlayer getPlayerFor(byte[] bytes, IAudioDevice pcmAudio) {
		SeekableBytes buffer_le=new SeekableBytes(bytes, Endian.LITTLEENDIAN);
		ModType type = ModType.MOD;
		identify: {
			byte byteArray4[] = new byte[4];
			buffer_le.seek(0);
			buffer_le.read(byteArray4, 0, 4);
			String format = new String(byteArray4).substring(0, 4);
			if (format.equals("IMPM")) {
				type=ModType.IT;
				break identify;
			}
			buffer_le.seek(0);
			buffer_le.forward(SCRM_SKIP);
			buffer_le.read(byteArray4, 0, 4);
			format = new String(byteArray4).substring(0, 4);
			if (format.equals("SCRM")) {
				type=ModType.S3M;
				break identify;
			}
			
			byte byteArray17[] = new byte[17];
			buffer_le.seek(0);
			buffer_le.read(byteArray17, 0, 17);
			format = new String(byteArray17).substring(0, 17);
			if (format.equals("Extended Module: ")) {
				type=ModType.XM;
				break identify;
			}
			/*
			 * Assume it is MOD and hope for the best...
			 */
			type=ModType.MOD;
		}
		switch(type) {
		case IT:
			ITPlayer itPlayer = new ITPlayer(pcmAudio);
			itPlayer.load(bytes);
			return itPlayer;
		case MOD:
			MODPlayer modPlayer = new MODPlayer(pcmAudio);
			modPlayer.load(bytes);
			return modPlayer;
		case S3M:
			S3MPlayer s3mPlayer = new S3MPlayer(pcmAudio);
			s3mPlayer.load(bytes);
			break;
		case XM:
			XMPlayer xmPlayer = new XMPlayer(pcmAudio);
			xmPlayer.load(bytes);
			return xmPlayer;
		default:
		}
		throw new OZModRuntimeError(ERR.BADFORMAT);
	}
}
