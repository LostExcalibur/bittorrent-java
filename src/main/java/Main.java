import com.google.gson.Gson;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
// import com.dampcake.bencode.Bencode;

public class Main {
	private static final Gson gson = new Gson();

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("""
					Available commands :
						decode <bencoded string>
						info <torrent file>
					""");
			return;
		}
		String command = args[0];
		BencodeDecoder decoder;
		switch (command) {
			case "decode": {
				if (args.length < 2) {
					System.err.println("Usage: decode <bencoded string>");
					return;
				}

				String bencodedValue = args[1];
				decoder = new BencodeDecoder(bencodedValue);
				Object decoded;
				try {
					decoded = decoder.parse();
				} catch (RuntimeException e) {
					System.err.println(e.getMessage());
					return;
				}
				System.out.println(gson.toJson(decoded));
				break;
			}
			case "info": {
				if (args.length < 2) {
					System.err.println("Usage: info <torrent file>");
					return;
				}
				String filename = args[1];
				byte[] info = readFile(filename);
				decoder = new BencodeDecoder(info);

				Map<String, Object> parsed = (Map<String, Object>) decoder.parse();
				String url = (String) parsed.get("announce");
				Map<String, Object> infoDict = (Map<String, Object>) parsed.get("info");
				Long length = (Long) infoDict.get("length");
				System.out.printf("Tracker URL: %s\nLength: %d", url, length);
				break;
			}
			default: {
				System.err.println("Unknown command: " + command);
			}
		}

	}

	static byte[] readFile(String filename) throws IOException {
		DataInputStream reader = new DataInputStream(new FileInputStream(filename));
		int nBytesToRead = reader.available();
		byte[] result;
		if(nBytesToRead > 0) {
			result = new byte[nBytesToRead];
			if (reader.read(result) != nBytesToRead) {
				System.err.println("Could not read entire file " + filename);
			}
		} else {
			throw new RuntimeException("Cannot read the file " + filename);
		}
		return result;
	}
}
