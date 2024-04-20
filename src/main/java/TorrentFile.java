import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TorrentFile {
	private final String url;
	private final long length;
	private final long pieceLength;
	private final List<byte[]> piecesList;
	private final byte[] infoHash;
	private long interval = -1;
	private Socket socket;
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

	public void handshake(InetSocketAddress peer) throws IOException {
		assert socket == null || socket.isClosed();
		socket = new Socket();
		socket.connect(peer);

		byte[] reserved = new byte[8]; // Should be all 0
		OutputStream outputStream = socket.getOutputStream();
		outputStream.write(19);
		outputStream.write("BitTorrent protocol".getBytes());
		outputStream.write(reserved);
		outputStream.write(infoHash);
		outputStream.write("00112233445566778899".getBytes());

		InputStream inputStream = socket.getInputStream();
		int length = inputStream.read();
		assert length == 19;

		String protocol = new String(inputStream.readNBytes(length));
		assert protocol.equals("BitTorrent protocol");

		inputStream.readNBytes(8); // Reserved, should be 0
		byte[] peerInfoHash = inputStream.readNBytes(20);
		assert Arrays.equals(peerInfoHash, infoHash);

		byte[] peerID = inputStream.readNBytes(20);
		System.out.println("Peer ID: " + hexString(peerID));
	}

	public byte[] downloadPiece(int pieceID) throws IOException {
		if (socket == null || socket.isClosed()) {
			System.out.println("Socket not initialized, handshaking first");
			handshake(peers.get(pieceID % peers.size()));
		}
		PeerMessage bitfield = new PeerMessage(socket.getInputStream());
		assert bitfield.type == PeerMessage.MessageType.BITFIELD;
		System.out.println("Received bitfield");
//		System.out.println(Integer.toString(bitfield.data[0] & 0xff, 2));

		byte[] empty = new byte[0];
		PeerMessage interested = new PeerMessage(PeerMessage.MessageType.INTERESTED, empty);
		interested.sendTo(socket.getOutputStream());
		System.out.println("Sent interested");

		PeerMessage unchoke = new PeerMessage(socket.getInputStream());
		assert unchoke.type == PeerMessage.MessageType.UNCHOKE;
		System.out.println("Received unchoke");

		int blockSize = 16 * 1024;
		long thisPieceLength = pieceID == piecesList.size() - 1 ? length % pieceLength : pieceLength;
		int blockCount = (int) (thisPieceLength / blockSize);
		if (blockCount == 0) {
			blockCount++;
		}
		System.out.printf("Length : %d, pieceLength : %d, blocksize: %d\n", length, thisPieceLength, blockSize);
		int remaining = (int) thisPieceLength;
		ByteBuffer pieceData = ByteBuffer.allocate(remaining);

		for (int i = 0; i < blockCount; i++) {
			// The last block might have smaller size if blocksize does not divide pieceLength
			int length = blockSize;
			if (remaining >= blockSize) {
				remaining -= blockSize;
			} else {
				length = remaining;
			}

			System.out.printf("Asking for %d bytes\n", length);

			ByteBuffer buffer = ByteBuffer.allocate(12);
			buffer.putInt(pieceID);
			buffer.putInt(i * blockSize);
			buffer.putInt(length);

			PeerMessage request = new PeerMessage(PeerMessage.MessageType.REQUEST, buffer.array());
			request.sendTo(socket.getOutputStream());
			System.out.println("Sent request");

			PeerMessage piece = new PeerMessage(socket.getInputStream());
			if (piece.type == PeerMessage.MessageType.PIECE) {
				System.out.println("Received piece");
			}

			ByteBuffer readBuffer = ByteBuffer.wrap(piece.data);
			int peerPieceID = readBuffer.getInt();
			int peerStartPos = readBuffer.getInt();
			if (peerPieceID != pieceID || peerStartPos != i * blockSize) {
				System.err.println("Mauvaise id ou position de départ");
				System.err.println("Peer sent wrong data, aborting");
				socket.close();
				return null;
			}

			pieceData.put(readBuffer);
		}

		byte[] array = pieceData.array().clone();
		System.out.printf("Asked for piece length of %d, got %d\n", thisPieceLength, array.length);
		if (!Arrays.equals(DigestUtils.sha1(array), piecesList.get(pieceID))) {
			System.err.println("Mauvais hash");
			System.err.println("Peer sent wrong data, aborting");
			socket.close();
			return null;
		}

		return pieceData.array();
	}


	public byte[] downloadFile() throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate((int) length);
		for (int pieceID = 0; pieceID < piecesList.size(); pieceID++) {
			byte [] piece = downloadPiece(pieceID);
			if (piece == null) {
				return null;
			}
			buffer.put(piece);
			System.out.printf("Downloaded piece %d out of %d\n", pieceID, piecesList.size());
			socket.close();
		}

		return buffer.array();
	}

	public void discoverPeers() {
		String fullUrl = url
				+ "?info_hash=" + URLEncoder.encode(new String(infoHash, StandardCharsets.ISO_8859_1), StandardCharsets.ISO_8859_1)
				+ "&peer_id=" + "00112233445566778899"
				+ "&port=" + "6881"
				+ "&uploaded=" + "0"
				+ "&downloaded=" + "0"
				+ "&left=" + Long.toString(length)
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
