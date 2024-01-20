import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TorrentFile {
	private final String url;
	private final Long length;
	private final Long pieceLength;
	private final List<byte[]> piecesList;
	private final byte[] infoHash;

	public TorrentFile(String filename) throws IOException {
		byte[] fileBytes = Files.readAllBytes(Paths.get(filename));
		BencodeDecoder decoder = new BencodeDecoder(fileBytes, true);
		BencodeEncoder encoder = new BencodeEncoder();

		Map<String, Object> fileData = (Map<String, Object>) decoder.parse();
		Map<String, Object> infoDict = (Map<String, Object>) fileData.get("info");

		encoder.encodeObject(infoDict);
		byte[] infoDictEncoded = encoder.getResult();

		this.url = new String((byte[]) fileData.get("announce"));
		this.length = (Long) infoDict.get("length");
		this.pieceLength = (Long) infoDict.get("piece length");

		byte[] pieces = (byte[]) infoDict.get("pieces");
		this.piecesList = separatePieceHashes(pieces);

		this.infoHash = DigestUtils.sha1(infoDictEncoded);

		System.out.printf("Tracker URL: %s\nLength: %d\nInfo Hash: %s\nPiece Length: %d\n", url, length, hexString(infoHash), pieceLength);

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
