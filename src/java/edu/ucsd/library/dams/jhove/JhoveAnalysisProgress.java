package edu.ucsd.library.dams.jhove;

import edu.harvard.hul.ois.jhove.Callback;

public class JhoveAnalysisProgress implements Callback {

	private long _filesize = 0L;

	public void set_filesize(long _filesize) {
		this._filesize = _filesize / 1024;
	}

	//TODO: this is not getting called for arg0 == 1 (Need to find out why)
	public int callback(int arg0, Object arg1) {
		if (arg0 == 1) {
			Long bytes = (Long) arg1;
			String message = bytes.longValue() / 1024L + " / " + this._filesize
					+ " kb";
			if (MyJhoveBase.shellMode) {
				String mesg = "JHOVE Analysis: " + message;
				System.out.println(mesg);
				return 0;
			}
		}
		return 0;
	}

}
