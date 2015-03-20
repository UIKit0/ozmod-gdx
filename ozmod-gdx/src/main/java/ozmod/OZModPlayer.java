package ozmod;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public abstract class OZModPlayer extends Thread {
	
	public static interface IAudioDevice {
		void writeSamples(short[] samples, int offset, int numSamples);
	};
	
	protected ChannelsList chansList_ = new ChannelsList();
	protected boolean done_ = false;
	protected int frequency_;
	protected final IAudioDevice gdxAudio;
	private boolean interrupted=false;
	protected boolean loopable_ = false;
	protected byte pcm_[];
	protected short[] pcms_;
	protected boolean running_;
	protected int speed_;	
	protected final String TAG;
	protected int tick_;
	protected final Timer timer_;
	/**
	 * Provide Interface To Audio device. Must accept 44.1KHz stereo audio!
	 * @param audioDevice
	 */
	public OZModPlayer(IAudioDevice audioDevice) {
		TAG=this.getClass().getSimpleName();
		gdxAudio = audioDevice;
		timer_=new Timer();
		setDaemon(true);
		setPriority(MIN_PRIORITY);
	}
	
	protected abstract void dispatchNotes();
	
	/**
	 * Stops the MOD. Once a MOD is stopped, it cannot be restarted.
	 */
	public void done() {
		running_ = false;
		try {
			join();
		} catch (InterruptedException e) {
		}
	}

	protected void doSleep(long ms) {
		if (ms<=0) {
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
		interrupted=true;
	}
	
	@Override
	public boolean isInterrupted() {
		return interrupted?true:super.isInterrupted();
	}
	
	protected void mixSample(int _time) {
		int nbsamp = frequency_ / (1000 / _time);
		Arrays.fill(pcm_, (byte) 0);
		chansList_.mix(nbsamp, pcm_);
		ByteBuffer.wrap(pcm_).order(ByteOrder.BIG_ENDIAN).asShortBuffer()
				.get(pcms_, 0, nbsamp * 2);
		gdxAudio.writeSamples(pcms_, 0, nbsamp * 2);
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
		running_=false;
		interrupt();
	}
	
	public abstract void load(byte[] bytes);

	public void setLoopable(boolean loopable) {
		loopable_=loopable;		
	}
}
