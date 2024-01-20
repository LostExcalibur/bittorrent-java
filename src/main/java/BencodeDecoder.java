import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BencodeDecoder {
	private static final Charset defaultCharset = StandardCharsets.UTF_8;
	private final InputStream inputStream;
	private final boolean useBytes;
	private final Charset charset;

	public BencodeDecoder(String input, Charset charset) {
		this(new ByteArrayInputStream(input.getBytes(charset)), charset, false);
	}

	public BencodeDecoder(byte[] input, boolean useBytes) {
		this(new ByteArrayInputStream(input), defaultCharset, useBytes);
	}

	public BencodeDecoder(InputStream inputStream, Charset charset, boolean useBytes) {
		if (!inputStream.markSupported()) {
			inputStream = new BufferedInputStream(inputStream);
		}
		this.inputStream = inputStream;
		this.charset = charset;
		this.useBytes = useBytes;
	}


	public Object parse() throws IOException {
		int first = peek();

		if (Character.isDigit(first)) {
			if (useBytes) {
				return parseBytes();
			} else {
				return parseString();
			}
		}
		if (first == 'i') {
			return parseInt();
		}
		if (first == 'l') {
			return parseList();
		}
		if (first == 'd') {
			return parseDict();
		}

		throw new RuntimeException("Wrong bencode format");
	}

	private byte[] parseBytes() throws IOException {
		int length = Integer.parseInt(readUntil(':'));
		return inputStream.readNBytes(length);
	}

	private Map<String, Object> parseDict() throws IOException {
		Map<String, Object> result = new HashMap<>();
		inputStream.skipNBytes(1);
		int next;

		while ((next = peek()) != 'e' && next != -1) {
			String key = useBytes ? new String((byte[]) parse()) : (String) parse();
			Object value = parse();
			result.put(key, value);
		}

		inputStream.skipNBytes(1);
		return result;
	}

	private List<Object> parseList() throws IOException {
		ArrayList<Object> result = new ArrayList<>();

		inputStream.skipNBytes(1);
		int next;

		while ((next = peek()) != 'e' && next != -1) {
			result.add(parse());
		}

		inputStream.skipNBytes(1);
		return result;
	}

	private String parseString() throws IOException {
		int length = Integer.parseInt(readUntil(':'));
		return new String(inputStream.readNBytes(length), charset);
	}

	private Long parseInt() throws IOException {
		inputStream.skipNBytes(1);
		return Long.parseLong(readUntil('e'));
	}

	private String readUntil(char e) throws IOException {
		StringBuilder builder = new StringBuilder();
		int value;
		while ((value = inputStream.read()) != -1 && value != e) {
			builder.append((char) value);
		}

		if (value == -1) {
			throw new RuntimeException("Unterminated sequence");
		}

		return builder.toString();
	}


	private int peek() throws IOException {
		inputStream.mark(1);
		int read = inputStream.read();
		inputStream.reset();
		return read;
	}
}
