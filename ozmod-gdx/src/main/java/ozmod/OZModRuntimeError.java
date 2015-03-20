package ozmod;

import ozmod.OZMod.ERR;

@SuppressWarnings("serial")
public class OZModRuntimeError extends RuntimeException {

	private final ERR err;

	public ERR getErr() {
		return err;
	}

	public OZModRuntimeError(ERR error) {
		this.err = error;
	}
	
	@Override
	public String getMessage() {
		return err.toString();
	}
	
	@Override
	public String getLocalizedMessage() {
		return err.toString();
	}

}
