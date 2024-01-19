import com.google.gson.Gson;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// import com.dampcake.bencode.Bencode;

public class Main {
	private static final Gson gson = new Gson();

	public static void main(String[] args) throws Exception {
		String command = args[0];
		if("decode".equals(command)) {
			String bencodedValue = args[1];
			Object decoded;
			try {
				decoded = decodeBencode(bencodedValue).getValue0();
			} catch(RuntimeException e) {
				System.out.println(e.getMessage());
				return;
			}
			System.out.println(gson.toJson(decoded));

		} else {
			System.out.println("Unknown command: " + command);
		}

	}

	static Pair<Object, String> decodeBencode(String bencodedString) {
		// Strings
		if (Character.isDigit(bencodedString.charAt(0))) {
			int firstColonIndex = 0;
			for(int i = 0; i < bencodedString.length(); i++) { 
				if(bencodedString.charAt(i) == ':') {
					firstColonIndex = i;
					break;
				}
			}
			int length = Integer.parseInt(bencodedString.substring(0, firstColonIndex));
			return Pair.with(bencodedString.substring(firstColonIndex+1, firstColonIndex+1+length), bencodedString.substring(firstColonIndex + 1 + length));

		// Integers
		} else if (bencodedString.charAt(0) == 'i') {
			int endIndex = bencodedString.indexOf('e');

			return Pair.with(Long.parseLong(bencodedString.substring(1, endIndex)), bencodedString.substring(endIndex + 1));

		// Lists
		} else if (bencodedString.charAt(0) == 'l') {
			List<Object> result = new ArrayList<>();
			String remaining = bencodedString.substring(1);
			do {
				Pair<Object, String> parsed = decodeBencode(remaining);
				remaining = parsed.getValue1();

				result.add(parsed.getValue0());

				if (remaining.charAt(0) == 'e') {
					return Pair.with(result, remaining.substring(1));
				}
			} while (true);

		// Dictionaries
		} else if (bencodedString.charAt(0) == 'd') {
			Map<String, Object> result = new HashMap<>();
			String remaining = bencodedString.substring(1);
			do {
				Pair<Object, String> parsedKey = decodeBencode(remaining);
				remaining = parsedKey.getValue1();
				Pair<Object, String> parsedValue = decodeBencode(remaining);
				remaining = parsedValue.getValue1();

				result.put((String) parsedKey.getValue0(), parsedValue.getValue0());

				if (remaining.charAt(0) == 'e') {
					return Pair.with(result, remaining.substring(1));
				}
			} while (true);

		} else {
			throw new RuntimeException("Wrong bencode format");
		}
	}
	
}
