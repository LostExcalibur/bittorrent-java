import com.google.gson.Gson;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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
				processTorrentFile(filename);
				break;
			}
			default: {
				System.err.println("Unknown command: " + command);
			}
		}
	}

	private static void processTorrentFile(String filename) throws IOException {
		byte[] fileBytes = Files.readAllBytes(Paths.get(filename));
		BencodeDecoder decoder = new BencodeDecoder(fileBytes, true);
		BencodeEncoder encoder = new BencodeEncoder(charset);

		Map<String, Object> fileData = (Map<String, Object>) decoder.parse();
		Map<String, Object> infoDict = (Map<String, Object>) fileData.get("info");

		encoder.encodeObject(infoDict);
		byte[] infoDictEncoded = encoder.getResult();

		String url = new String((byte[]) fileData.get("announce"));
		Long length = (Long) infoDict.get("length");
		Long pieceLength = (Long) infoDict.get("piece length");
		byte[] pieces = (byte[]) infoDict.get("pieces");
		List<byte[]> piecesList = separatePieceHashes(pieces);

		String infoHash = DigestUtils.sha1Hex(infoDictEncoded);

		System.out.printf("Tracker URL: %s\nLength: %d\nInfo Hash: %s\nPiece Length: %d\n", url, length, infoHash, pieceLength);

		System.out.println("Piece Hashes:");
		for (byte[] piece : piecesList) {
			System.out.println(hexString(piece));
		}
	}

	private static List<byte[]> separatePieceHashes(byte[] pieces) throws IOException {
		List<byte[]> piecesList = new ArrayList<>();
		ByteArrayInputStream inputStream = new ByteArrayInputStream(pieces);

		while (inputStream.available() >= 20) {
			piecesList.add(inputStream.readNBytes(20));
		}

		if (inputStream.available() >= 20) {
			System.err.printf("Warning : %d too many bytes in pieces property", inputStream.available());
		}

		return piecesList;
	}

	private static String hexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}

		return sb.toString();
	}
}
