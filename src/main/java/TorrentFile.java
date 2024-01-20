import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
	private long interval = -1;
	private List<InetSocketAddress> peers;

	public TorrentFile(String filename) throws IOException {
		byte[] fileBytes = Files.readAllBytes(Paths.get(filename));
		BencodeDecoder decoder = new BencodeDecoder(fileBytes, true);
		BencodeEncoder encoder = new BencodeEncoder();

		Map<String, Object> fileData = (Map<String, Object>) decoder.parse();
		Map<String, Object> infoDict = (Map<String, Object>) fileData.get("info");

		encoder.encodeObject(infoDict);
		byte[] infoDictEncoded = encoder.getResult();

		// TODO : Support multiple trackers (see https://www.bittorrent.org/beps/bep_0012.html)
		// TODO : Support UDP
		this.url = new String((byte[]) fileData.get("announce"));
		this.length = (Long) infoDict.get("length");
		this.pieceLength = (Long) infoDict.get("piece length");

		byte[] pieces = (byte[]) infoDict.get("pieces");
		this.piecesList = separatePieceHashes(pieces);

		this.infoHash = DigestUtils.sha1(infoDictEncoded);
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

	public void discoverPeers() {
		String fullUrl = url
				+ "?info_hash=" + URLEncoder.encode(new String(infoHash, StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1)
				+ "&peer_id=" + "00112233445566778899"
				+ "&port=" + "6881"
				+ "&uploaded=" + "0"
				+ "&downloaded=" + "0"
				+ "&left=" + length.toString()
				+ "&compact=" + "1";
		HttpGet getRequest = new HttpGet(fullUrl);
		CloseableHttpClient client = HttpClients.createDefault();

		try (CloseableHttpResponse HTTPRresponse = client.execute(getRequest)) {
			byte[] bencodedResponse = HTTPRresponse.getEntity().getContent().readAllBytes();

			BencodeDecoder decoder = new BencodeDecoder(bencodedResponse, true);
			Map<String, Object> responseDict = (Map<String, Object>) decoder.parse();

			if (responseDict.containsKey("failure reason")) {
				throw new RuntimeException(new String((byte[]) responseDict.get("failure reason")));
			}

			interval = (Long) responseDict.get("interval");
			byte[] peersBytes = (byte[]) responseDict.get("peers");
			peers = decodePeers(peersBytes);

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private List<InetSocketAddress> decodePeers(byte[] peersBytes) throws IOException {
		List<InetSocketAddress> result = new ArrayList<>();
		ByteArrayInputStream peersBuffer = new ByteArrayInputStream(peersBytes);

		while (peersBuffer.available() > 0) {
			byte[] ip = peersBuffer.readNBytes(4);
			short port = ByteBuffer.wrap(peersBuffer.readNBytes(2)).getShort();
			InetAddress addr = InetAddress.getByAddress(ip);
			InetSocketAddress socketAddress = new InetSocketAddress(addr, port & 0xffff);
			result.add(socketAddress);
		}

		return result;
	}

	public void printPeers() {
		for (InetSocketAddress peer : peers) {
			System.out.printf("%s:%d\n", peer.getHostString(), peer.getPort());
		}
	}

	public void printInfo() {
		System.out.printf("Tracker URL: %s\nLength: %d\nInfo Hash: %s\nPiece Length: %d\n", url, length, hexString(infoHash), pieceLength);

		System.out.println("Piece Hashes:");
		for (byte[] piece : piecesList) {
			System.out.println(hexString(piece));
		}
	}
}
