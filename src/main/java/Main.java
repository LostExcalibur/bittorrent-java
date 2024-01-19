import com.google.gson.Gson;
// import com.dampcake.bencode.Bencode;

public class Main {
	private static final Gson gson = new Gson();

	public static void main(String[] args) throws Exception {
		String command = args[0];
		if("decode".equals(command)) {
			String bencodedValue = args[1];
			Object decoded;
			try {
				decoded = decodeBencode(bencodedValue);
			} catch(RuntimeException e) {
				System.out.println(e.getMessage());
				return;
			}
			System.out.println(gson.toJson(decoded));

		} else {
			System.out.println("Unknown command: " + command);
		}

	}

	static Object decodeBencode(String bencodedString) {
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
			return bencodedString.substring(firstColonIndex+1, firstColonIndex+1+length);

		// Integers
		} else if (bencodedString.charAt(0) == 'i') {
			int endIndex = bencodedString.indexOf('e');

			return Long.parseLong(bencodedString.substring(1, endIndex));
		} else {
			throw new RuntimeException("Only strings and integers are supported at the moment");
		}
	}
	
}
