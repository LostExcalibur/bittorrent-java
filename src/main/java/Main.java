import com.google.gson.Gson;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

		switch (command) {
			case "decode": {
				if (args.length < 2) {
					System.err.println("Usage: decode <bencoded string>");
					return;
				}

				String bencodedValue = args[1];
				BencodeDecoder decoder = new BencodeDecoder(bencodedValue, charset);

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
				TorrentFile torrent = new TorrentFile(filename);
				break;
			}
			default: {
				System.err.println("Unknown command: " + command);
			}
		}
	}
}
