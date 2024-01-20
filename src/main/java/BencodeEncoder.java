import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BencodeEncoder {
	private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	private final Charset charset;
	private static final Charset defaultCharset = StandardCharsets.UTF_8;

	public BencodeEncoder() {
		this(defaultCharset);
	}

	public BencodeEncoder(Charset charset) {
		this.charset = charset;
	}

	public byte[] getResult() {
		byte[] result = outputStream.toByteArray();
		outputStream.reset();
		return result;
	}

	public void encodeObject(Object obj) throws IOException {
		if (obj == null) {
			throw new NullPointerException("Cannot encode null object");
		}
		switch (obj) {
			case String s -> encodeString(s);
			case byte[] bytes -> encodeBytes(bytes);
			case Long l -> encodeNumber(l);
			case Map<?, ?> map -> encodeMap(map);
			case List<?> objects -> encodeList(objects);
			default -> throw new RuntimeException("Can only encode numbers, strings, lists and maps");
		}
	}

	private void encodeBytes(byte[] bytes) throws IOException {
		outputStream.write(Integer.toString(bytes.length).getBytes(this.charset));
		outputStream.write(58);
		outputStream.write(bytes);
	}

	private void encodeList(List<?> obj) throws IOException {
		outputStream.write(108);
		for (Object o : obj) {
			encodeObject(o);
		}
		outputStream.write(101);
	}

	private void encodeMap(Map<?, ?> obj) throws IOException {
		Map m = new TreeMap(obj);
		outputStream.write(100);
		for (Object e : m.entrySet()) {
			Map.Entry<?, ?> entry = (Map.Entry<?, ?>) e;
			encodeObject(entry.getKey());
			encodeObject(entry.getValue());
		}
		outputStream.write(101);
	}

	private void encodeNumber(Long obj) throws IOException {
		outputStream.write(105);
		outputStream.write(Long.toString(obj).getBytes(this.charset));
		outputStream.write(101);
	}

	private void encodeString(String obj) throws IOException {
		outputStream.write(Integer.toString(obj.getBytes().length).getBytes(this.charset));
		outputStream.write(58);
		outputStream.write(obj.getBytes(this.charset));
	}
}
