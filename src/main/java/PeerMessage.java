import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class PeerMessage {
	MessageType type;
	int size;
	byte[] data = null;

	public PeerMessage(InputStream stream) throws IOException {
		byte[] array = stream.readNBytes(4);
		while (array.length == 0) {
			// Keepalive (sure ?), ignore and go to the next
			array = stream.readNBytes(4);
		}
		int size = ByteBuffer.wrap(array).getInt();
//		System.out.println(size);
		type = MessageType.fromInt(stream.read());
		if (size > 1) {
			data = stream.readNBytes(size - 1);
		}
//		System.out.printf("Received message of size %d with type %s and payload size %s\n", size, type.name(), data == null ? 0 : data.length);
	}

	public PeerMessage(MessageType type, byte[] payload) {
		this.type = type;
		this.data = payload;
		this.size = 1 + this.data.length;
	}

	public void sendTo(OutputStream stream) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(5 + data.length);
		buffer.putInt(size);
		buffer.put((byte) type.ordinal());
		buffer.put(data);

		stream.write(buffer.array());
	}

	public enum MessageType {
		CHOKE, UNCHOKE, INTERESTED, NOT_INTERESTED, HAVE, BITFIELD, REQUEST, PIECE, CANCEL;
		private static MessageType[] values = null;

		public static MessageType fromInt(int i) {
			if (MessageType.values == null) {
				MessageType.values = MessageType.values();
			}
			return MessageType.values[i];
		}
	}
}
