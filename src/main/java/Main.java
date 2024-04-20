import com.google.gson.Gson;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
						peers <torrent file>
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
				torrent.printInfo();
				break;
			}
			case "peers": {
				if (args.length < 2) {
					System.err.println("Usage: peers <torrent file>");
					return;
				}
				String filename = args[1];
				TorrentFile torrent = new TorrentFile(filename);
				torrent.discoverPeers();
				torrent.printPeers();
				break;
			}
			case "handshake": {
				if (args.length < 3) {
					System.err.println("Usage: handshake <torrent file> <ip>:<port>");
					return;
				}
				String filename = args[1];
				TorrentFile torrent = new TorrentFile(filename);
//				torrent.discoverPeers();
				String peerAddress = args[2];
				String[] split = peerAddress.split(":");
				if (split.length != 2) {
					throw new RuntimeException("Wrong peer format, should be <ip>:<port>");
				}
				InetSocketAddress peer = new InetSocketAddress(split[0], Integer.parseInt(split[1]));
				torrent.handshake(peer);
				break;
			}
			case "download_piece": {
				if (args.length < 5) {
					System.err.println("Usage: download_piece -o <output file> <torrent file> <piece id>");
					return;
				}

				String outputFile = args[2];
				String torrentFile = args[3];
				int pieceID = Integer.parseInt(args[4]);

				TorrentFile torrent = new TorrentFile(torrentFile);
				torrent.discoverPeers();
				byte[] piece = torrent.downloadPiece(pieceID);

				Files.write(Paths.get(outputFile), piece);
				System.out.printf("Piece %d downloaded to %s.\n", pieceID, outputFile);

				break;
			}
			case "download": {
				if (args.length < 4) {
					System.err.println("Usage: download -o <output file> <torrent file>");
					return;
				}
				String outputFile = args[2];
				String torrentFile = args[3];

				TorrentFile torrent = new TorrentFile(torrentFile);
				torrent.discoverPeers();
				byte[] fileData;
				if ((fileData = torrent.downloadFile()) != null) {
					Files.write(Paths.get(outputFile), fileData);
					System.out.printf("Downloaded %s to %s\n", torrentFile, outputFile);
				}

				break;
			}
			default: {
				System.err.println("Unknown command: " + command);
			}
		}
	}
}
