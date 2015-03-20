package ozmod;

import ozmod.OZMod.ERR;

@SuppressWarnings("serial")
public class OZModRuntimeError extends RuntimeException {

	private ERR err;

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
