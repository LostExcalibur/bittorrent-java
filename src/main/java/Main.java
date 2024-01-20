import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
// import com.dampcake.bencode.Bencode;

public class Main {
	private static final Gson gson = new Gson();
	private static final Charset charset = StandardCharsets.UTF_8;

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
				decoder = new BencodeDecoder(bencodedValue, charset);

				Object decoded = decoder.parse();
				System.out.println(gson.toJson(decoded));
				break;
			}
			case "info": {
				if (args.length < 2) {
					System.err.println("Usage: info <torrent file>");
					return;
				}
				String filename = args[1];
				byte[] fileBytes = Files.readAllBytes(Paths.get(filename));

				decoder = new BencodeDecoder(fileBytes, true);
				Map<String, Object> fileData = (Map<String, Object>) decoder.parse();
				Map<String, Object> infoDict = (Map<String, Object>) fileData.get("info");

				BencodeEncoder encoder = new BencodeEncoder(charset);
				encoder.encodeObject(infoDict);
				byte[] testDecoded = encoder.getResult();

				String url = new String((byte[]) fileData.get("announce"));
				Long length = (Long) infoDict.get("length");
				String infoHash = DigestUtils.sha1Hex(testDecoded);

				System.out.printf("Tracker URL: %s\nLength: %d\nInfo Hash: %s", url, length, infoHash);
				break;
			}
			default: {
				System.err.println("Unknown command: " + command);
			}
		}

	}
}
