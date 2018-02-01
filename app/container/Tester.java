// File IO
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;

// JSON
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.WriterConfig;
import com.eclipsesource.json.ParseException;

import java.lang.reflect.InvocationTargetException;

public class Tester {
  public static String jsonString;

  public static Class<?>[] parameterTypes;
  public static Class<?> resultType;
  public static TestCase[] testCases;

  public static TestSuite suite;

  // Constructor reads all info and initializes a TestSuite
  public static void init() throws IOException {
    // Read + parse JSON from file
    Tester.jsonString = Tester.readFile("./tests.json");
    JsonObject fullJson = Json.parse(Tester.jsonString).asObject();

    // Parse parameter types from JSON into an array of Classes
    JsonArray jsonParameterTypes = fullJson.get("parameterTypes").asArray();
    Object[] parameterTypeNames = (Object[]) JsonInterpret.toObject(jsonParameterTypes);
    Tester.parameterTypes = new Class<?>[parameterTypeNames.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      Tester.parameterTypes[i] = JsonInterpret.getJavaType((String) parameterTypeNames[i]);
    }

    // Extract expected result type from JSON to a Class
    Tester.resultType = JsonInterpret.getJavaType(fullJson.get("resultType").asString());

    // Build an array of TestCases
    JsonArray jsonTestCases = fullJson.get("testCases").asArray(); // JSON representation
    Tester.testCases = new TestCase[jsonTestCases.size()];     // Array to be filled
    for (int i = 0; i < Tester.testCases.length; i++) {
      // Get JSON representation of test case
      JsonObject jsonTestCase = jsonTestCases.get(i).asObject();
      // Extract parameters as array of Java objects
      Object[] parameters = (Object[]) JsonInterpret.toObject(jsonTestCase.get("parameters").asArray());
      // Extract expected result as Java object
      Object expectedResult = JsonInterpret.toObject(jsonTestCase.get("result"));
      // Create the instance of the TestCase class
      Tester.testCases[i] = new TestCase(parameters, expectedResult);
    }

    // Initialize TestSuite
    Tester.suite = new TestSuite(Tester.testCases, Tester.parameterTypes, Tester.resultType);
  }


  // Returns a String with the contents of a file
  static String readFile(String path, Charset encoding) throws IOException {
    byte[] bytes = Files.readAllBytes(Paths.get(path));
    return new String(bytes, encoding);
  }

  // Default encoding of UTF-8
  static String readFile(String path) throws IOException {
    return Tester.readFile(path, StandardCharsets.UTF_8);
  }


  // Writes a String to a file with a given name
  static void writeFile(String path, String content, Charset encoding) throws IOException {
    Files.write(Paths.get(path), content.getBytes(encoding));
  }

  // Default encoding of UTF-8
  static void writeFile(String path, String content) throws IOException {
    Tester.writeFile(path, content, StandardCharsets.UTF_8);
  }


  // Put stack trace into a string
  static String exceptionToString(Throwable e) {
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }


  // Calls suite.run() and reports the results, including handling errors
  public static void main(String[] args) throws IOException {
    JsonObject out = Json.object();

    try {
      Tester.init();
      TestResult[] results = Tester.suite.run();

      // Serialize test results as Json
      JsonArray jsonResults = Json.array();
      for (TestResult r : results) jsonResults.add(
        Json.object()
          .add("value", JsonInterpret.getJsonValue(r.value, resultType))
          .add("expected", JsonInterpret.getJsonValue(r.expected, resultType))
          .add("pass", r.pass)
      );
      // Add to output
      out.add("data", jsonResults);
    } catch (IOException e) {
      out.add("error", "Could not read input files");
    } catch (ParseException e) {
      out.add("error", "Could not parse JSON");
    } catch (Exception e) {
      out.add("error", Tester.exceptionToString(e));
    }

    // Save to out.json
    Tester.writeFile("./out.json", out.toString(WriterConfig.PRETTY_PRINT));
  }
}
