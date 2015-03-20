package ozmod;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public abstract class OZModPlayer extends Thread {

	public static interface IAudioDevice {
		void writeSamples(short[] samples, int offset, int numSamples);
	};

	protected ChannelsList chansList_ = new ChannelsList();
	protected boolean done_ = false;
	protected int frequency_;
	protected final IAudioDevice pcmAudio;
	private boolean interrupted = false;
	protected boolean loopable_ = false;
	protected byte pcm_[];
	protected short[] pcms_;
	protected boolean running_;
	protected int speed_;
	protected final String TAG;
	protected int tick_;
	protected final Timer timer_;
	protected boolean paused;

	/**
	 * Provide Interface To Audio device. Must accept 44.1KHz stereo audio!
	 * 
	 * @param audioDevice
	 */
	public OZModPlayer(IAudioDevice audioDevice) {
		TAG = this.getClass().getSimpleName();
		pcmAudio = audioDevice;
		timer_ = new Timer();
		setDaemon(true);
		setPriority(MIN_PRIORITY);
	}

	protected abstract void dispatchNotes();

	/**
	 * Stops playing and calls "doWhenDone" runnables.
	 */
	public void done() {
		running_ = false;
		try {
			join();
		} catch (InterruptedException e) {
		}		
		doWhenDone();
	}

	private void doWhenDone() {
		done_ = true;
		synchronized (this) {
			for (Runnable r : whenDone) {
				new Thread(r).start();
			}
			whenDone.clear();
		}
	}
	
	/**
	 * Stops playing and does NOT call "doWhenDone" runnables.
	 */
	public void cancel(){
		synchronized (this) {
			whenDone.clear();
			done();
		}
	}

	private Set<Runnable> whenDone = new HashSet<Runnable>();

	public void addWhenDone(Runnable whenDone) {
		this.whenDone.add(whenDone);
	}

	public void removeWhenDone(Runnable whenDone) {
		this.whenDone.remove(whenDone);
	}

	public void clearWhenDone() {
		this.whenDone.clear();
	}

	protected void doSleep(long ms) {
		if (ms <= 0) {
			return;
		}
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}

	@Override
	public void interrupt() {
		super.interrupt();
		interrupted = true;
	}

	@Override
	public boolean isInterrupted() {
		return interrupted ? true : super.isInterrupted();
	}

	protected void mixSample(int _time) {
		int nbsamp = frequency_ / (1000 / _time);
		Arrays.fill(pcm_, (byte) 0);
		chansList_.mix(nbsamp, pcm_);
		ByteBuffer.wrap(pcm_).order(ByteOrder.BIG_ENDIAN).asShortBuffer()
				.get(pcms_, 0, nbsamp * 2);
		pcmAudio.writeSamples(pcms_, 0, nbsamp * 2);
	}

	protected abstract void oneShot(int timer);

	public abstract void play();

	/**
	 * Never call this method directly. Use play() instead.
	 */
	public abstract void run();

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		running_ = false;
		interrupt();
	}

	public abstract void load(byte[] bytes);

	public void setLoopable(boolean loopable) {
		loopable_ = loopable;
	}

	public void pause(boolean b) {
		paused = b;
	}

	/**
	 * Gets the current reading position of the song.
	 * 
	 * @return The current position.
	 */
	public abstract int getCurrentPos();

	/**
	 * Gets the current reading row of the song.
	 * 
	 * @return The current row.
	 */
	public abstract int getCurrentRow();

	/**
	 * Gets the internal buffer used to mix the samples together. It can be used
	 * for instance to analyze the wave, apply effect or whatever.
	 * 
	 * @return The internal mix buffer.
	 */
	public abstract byte[] getMixBuffer();

	/**
	 * Tells if the XM is loopable or not.
	 * 
	 * @return true if loopable, false otherwhise.
	 */
	public boolean isLoopable() {
		return loopable_;
	}

	public abstract void setVolume(float _vol);

	public abstract float getVolume();
}
