package net.sourceforge.sox;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ffmpeg.android.BinaryInstaller;
import org.ffmpeg.android.ShellUtils.ShellCallback;

import android.content.Context;
import android.util.Log;

public class SoxController {
	private final static String TAG = "SOX";
	String[] libraryAssets = {"sox"};
	private String soxBin;
	private File fileBinDir;
	private Context context;

	public SoxController(Context _context) throws FileNotFoundException, IOException {
		context = _context;
		fileBinDir = context.getDir("bin",0);

		if (!new File(fileBinDir, libraryAssets[0]).exists())
		{
			BinaryInstaller bi = new BinaryInstaller(context,fileBinDir);
			bi.installFromRaw();
		}

		soxBin = new File(fileBinDir,"sox").getAbsolutePath();

	}

	private class LoggingCallback implements ShellCallback {
		@Override
		public void shellOut(String shellLine) {
			Log.i("sox", shellLine);
		}

		@Override
		public void processComplete(int exitValue) {
			Log.i("sox", "Got return value: " + exitValue);
		}
	}

	private class LengthParser implements ShellCallback {
		public double length;
		public int retValue = -1;

		@Override
		public void shellOut(String shellLine) {
			Log.d("sox", shellLine);
			if( !shellLine.startsWith("Length") )
				return;
			String[] split = shellLine.split(":");
			if(split.length != 2) return;

			String lengthStr = split[1].trim();

			try {
				length = Double.parseDouble( lengthStr );
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void processComplete(int exitValue) {
			retValue = exitValue;

		}
	}
	
	/**
	 * Retrieve the length of the audio file
	 * sox file.wav 2>&1 -n stat | grep Length | cut -d : -f 2 | cut -f 1
	 * @return the length in seconds or null
	 */
	public double getLength(String path) {
		ArrayList<String> cmd = new ArrayList<String>();

		cmd.add(soxBin);
		cmd.add(path);
		cmd.add("-n");
		cmd.add("stat");

		LengthParser sc = new LengthParser();

		try {
			execSox(cmd, sc);
		} catch (IOException e) {
			Log.e("sox","error getting length ",e);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			Log.e("sox","error getting length",e);
		}

		return sc.length;
	}

	/**
	 * Discard all audio not between start and length (length = end by default)
	 * sox <path> -e signed-integer -b 16 outFile trim <start> <length>
	 * @param start
	 * @param length (optional)
	 * @return path to trimmed audio
	 */
	public String trimAudio(String path, String start, String length) {
		ArrayList<String> cmd = new ArrayList<String>();

		File file = new File(path);
		String outFile = file.getAbsolutePath() + "_trimmed.wav";
		cmd.add(soxBin);
		cmd.add(path);
		cmd.add("-e");
		cmd.add("signed-integer");
		cmd.add("-b");
		cmd.add("16");
		cmd.add(outFile);
		cmd.add("trim");
		cmd.add(start);
		if( length != null )
			cmd.add(length);

		try {
			int rc = execSox(cmd, new LoggingCallback());
			if( rc != 0 ) {
				Log.e(TAG, "trimAudio receieved non-zero return code!");
				outFile = null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return outFile;
	}

	/**
	 * Fade audio file
	 * sox <path> outFile fade <type> <fadeInLength> <stopTime> <fadeOutLength>
	 * @param path
	 * @param type
	 * @param fadeInLength specify 0 if no fade in is desired
	 * @param stopTime (optional)
	 * @param fadeOutLength (optional)
	 * @return
	 */
	public String fadeAudio(String path, String type, String fadeInLength, String stopTime, String fadeOutLength ) {

		final List<String> curves = Arrays.asList( new String[]{ "q", "h", "t", "l", "p"} );

		if(!curves.contains(type)) {
			Log.e(TAG, "fadeAudio: passed invalid type: " + type);
			return null;
		}

		File file = new File(path);
		String outFile = file.getAbsolutePath() + "_faded.wav";

		ArrayList<String> cmd = new ArrayList<String>();
		cmd.add(soxBin);
		cmd.add(path);
		cmd.add(outFile);
		cmd.add("fade");
		cmd.add(type);
		cmd.add(fadeInLength);
		if(stopTime != null)
			cmd.add(stopTime);
		if(fadeOutLength != null)
			cmd.add(fadeOutLength);

		try {
			int rc = execSox(cmd, new LoggingCallback());
			if(rc != 0) {
				Log.e(TAG, "fadeAudio receieved non-zero return code!");
				outFile = null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return outFile;
	}

	/**
	 * Combine and mix audio files
	 * sox -m -v 1.0 file[0] -v 1.0 file[1] ... -v 1.0 file[n] outFile
	 * TODO support passing of volume
	 * @param files
	 * @return combined and mixed file (null on failure)
	 */
	public String combineMix(List<String> files, String outFile) {
		ArrayList<String> cmd = new ArrayList<String>();
		cmd.add(soxBin);
		cmd.add("-m");

		for(String file : files) {
			cmd.add("-v");
			cmd.add("1.0");
			cmd.add(file);
		}
		cmd.add(outFile);

		try {
			int rc = execSox(cmd, new LoggingCallback());
			if(rc != 0) {
				Log.e(TAG, "combineMix receieved non-zero return code!");
				outFile = null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return outFile;
	}

	/**
	 * Simple combiner
	 * sox file[0] file[1] ... file[n] <outFile>
	 * @param files
	 * @param outFile
	 * @return outFile or null on failure
	 */
	public String combine(List<String> files, String outFile) {
		ArrayList<String> cmd = new ArrayList<String>();
		cmd.add(soxBin);

		for(String file : files) {
			cmd.add(file);
		}
		cmd.add(outFile);

		try {
			int rc = execSox(cmd, new LoggingCallback());
			if(rc != 0) {
				Log.e(TAG, "combine receieved non-zero return code!");
				outFile = null;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return outFile;
	}

	/**
	 * Takes a seconds.frac value and formats it into:
	 * 	hh:mm:ss:ss.frac
	 * @param seconds
	 */
	public String formatTimePeriod(double seconds) {
		String seconds_frac = new DecimalFormat("#.##").format(seconds);
		return String.format("0:0:%s", seconds_frac);
	}

	private int execSox(List<String> cmd, ShellCallback sc) throws IOException,
			InterruptedException {

		String soxBin = new File(fileBinDir, "sox").getAbsolutePath();
		Runtime.getRuntime().exec("chmod 700 " + soxBin);
		return execProcess(cmd, sc);
	}

	private int execProcess(List<String> cmds, ShellCallback sc)
			throws IOException, InterruptedException {

		ProcessBuilder pb = new ProcessBuilder(cmds);
		pb.directory(fileBinDir);

		StringBuffer cmdlog = new StringBuffer();

		for (String cmd : cmds) {
			cmdlog.append(cmd);
			cmdlog.append(' ');
		}

		Log.v(TAG, cmdlog.toString());

		// pb.redirectErrorStream(true);
		Process process = pb.start();

		// any error message?
		StreamGobbler errorGobbler = new StreamGobbler(
				process.getErrorStream(), "ERROR", sc);

		// any output?
		StreamGobbler outputGobbler = new StreamGobbler(
				process.getInputStream(), "OUTPUT", sc);

		// kick them off
		errorGobbler.start();
		outputGobbler.start();

		int exitVal = process.waitFor();

		while (outputGobbler.isAlive() || errorGobbler.isAlive());
		
		sc.processComplete(exitVal);

		return exitVal;
	}

	class StreamGobbler extends Thread {
		InputStream is;
		String type;
		ShellCallback sc;

		StreamGobbler(InputStream is, String type, ShellCallback sc) {
			this.is = is;
			this.type = type;
			this.sc = sc;
		}

		public void run() {
			try {
				InputStreamReader isr = new InputStreamReader(is);
				BufferedReader br = new BufferedReader(isr);
				String line = null;
				while ((line = br.readLine()) != null)
					if (sc != null)
						sc.shellOut(line);

			} catch (IOException ioe) {
				Log.e(TAG, "error reading shell slog", ioe);
			}
		}
	}
}
