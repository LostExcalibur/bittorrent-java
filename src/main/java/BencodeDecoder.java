import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BencodeDecoder {
	private final InputStream inputStream;

	public BencodeDecoder(String input) {
		this(new ByteArrayInputStream(input.getBytes()));
	}

	public BencodeDecoder(byte[] input) {
		this(new ByteArrayInputStream(input));
	}

	public BencodeDecoder(InputStream inputStream) {
		if (!inputStream.markSupported()) {
			inputStream = new BufferedInputStream(inputStream);
		}
		this.inputStream = inputStream;
	}


	public Object parse() throws IOException {
		int first = peek();

		if (Character.isDigit(first)) {
			return parseString();
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

	private Map<String, Object> parseDict() throws IOException {
		Map<String, Object> result = new HashMap<>();
		inputStream.skipNBytes(1);
		int next;

		while ((next = peek()) != 'e' && next != -1) {
			String key = (String) parse();
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
		return new String(inputStream.readNBytes(length), StandardCharsets.US_ASCII);
	}

	private Long parseInt() throws IOException {
		inputStream.skipNBytes(1);
		return Long.parseLong(readUntil('e'));
	}

	private String readUntil(char e) throws IOException {
		StringBuilder builder = new StringBuilder();
		int value;
		while ((value = inputStream.read()) != -1 && value != e) {
			builder.append((char)value);
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
