package de.zonlykroks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import de.maxhenkel.simpleconfig.Configuration;
import de.maxhenkel.simpleconfig.PropertyConfiguration;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.fitting.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

public class LGS {
    private PolynomialFunction polynomialFunction;
    private List<Double> xValues;
    private List<Double> yValues;
    private int degree;
    private double[] coefficients;
    private Map<Double, Double> values;

    private String fileName;

    private final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    private Configuration config;

    // Constants for command strings
    private static final String CMD_EXIT = "exit";
    private static final String CMD_PRINT = "print";
    private static final String CMD_SIZES = "sizes";
    private static final String CMD_PRICES = "prices";
    private static final String CMD_DEGREE = "degree";
    private static final String CMD_COEFFICIENTS = "coefficients";
    private static final String CMD_VALUES = "values";
    private static final String CMD_RELOAD = "reload";
    private static final String CMD_LOAD = "load";
    private static final String CMD_EXPORT = "export";

    private String curveFitterType;

    public static final Logger logger = LoggerFactory.getLogger(LGS.class);

    public static void main(String[] args) {
        new LGS();
    }

    public LGS() {
        try {
            createConfigFolder();

            File file = new File(System.getProperty("user.dir") + "/config/config.properties");

            if (!file.exists()) {
                boolean successfull = file.createNewFile();

                if (successfull) {
                    logger.info("Config file created!");
                } else {
                    logger.error("Failed to create config file!");
                    return;
                }
            }

            this.config = new PropertyConfiguration(file);
        }catch (IOException e) {
            logger.error("Failed to load config file: {}", e.getMessage());
            return;
        }

        try (Scanner scanner = new Scanner(System.in)) {
            logger.info("Welcome to the Fish Price Calculator!");
            logger.info("Made by zOnlyKroks for the StateMC Server");

            logger.info("This program uses a Polynomial Function to calculate the Fish Price based on the Fish Size");
            logger.info("The Fish Size and Fish Price Samples are stored in a JSON5 file in the config folder");

            logger.info("Input the file name of the JSON5 file in the config folder (without the file extension):");

            this.fileName = scanner.next();

            getOrReloadData(this.fileName);

            while (true) {
                logger.info("Input Fish Size to get Fish Price or another command: " +
                        "print, sizes, prices, degree, coefficients, values, reload, export, exit");

                String input = scanner.next();

                if (input.equalsIgnoreCase(CMD_EXIT)) {
                    logger.info("Exiting program...");
                    logger.info("Goodbye!");
                    logger.info("Made with love :3");
                    break;
                }

                switch (input) {
                    case CMD_PRINT:
                        logger.info("Approximate Function for given Fish Values: {}", polynomialFunction);
                        continue;
                    case CMD_SIZES:
                        logger.info("Fish Sizes Values: {}", this.xValues);
                        continue;
                    case CMD_PRICES:
                        logger.info("Fish Prize Values: {}", this.yValues);
                        continue;
                    case CMD_DEGREE:
                        logger.info("Function Degree: {}", this.degree);
                        continue;
                    case CMD_COEFFICIENTS:
                        logger.info("Function Coefficients: {}", Arrays.toString(this.coefficients));
                        continue;
                    case CMD_VALUES:
                        logger.info("Function Values: {}", this.values);
                        continue;
                    case CMD_RELOAD:
                        logger.info("Reloading data from the last file: {}", this.fileName);
                        getOrReloadData( this.fileName);
                        continue;
                    case CMD_LOAD:
                        logger.info("Input the file name of the JSON5 file in the config folder (without the file extension):");
                        this.fileName = scanner.next();
                        getOrReloadData(this.fileName);
                        continue;
                    case CMD_EXPORT:
                        exportCalcFunc();
                        continue;
                    default:
                        try {
                            int x = Integer.parseInt(input);
                            logger.info("Exact Fish Price: {}", polynomialFunction.value(x));
                            logger.info("Rounded Fish Price: {}", Math.round(polynomialFunction.value(x)));
                        } catch (NumberFormatException e) {
                            logger.info("Invalid input. Please enter a valid command or Fish Size.");
                        }
                }
            }
        } catch (Exception e) {
            logger.error("An error occurred: {}", e.getMessage());
            logger.error("Aborting program...");
        }
    }

    private double[] solve(List<Double> xValues, List<Double> yValues, AbstractCurveFitter fitter) {
        WeightedObservedPoints obs = new WeightedObservedPoints();

        for (int i = 0; i < xValues.size(); i++) {
            obs.add(xValues.get(i), yValues.get(i));
        }

        return fitter.fit(obs.toList());
    }


    public void getOrReloadData(String fileName) throws IOException {
        this.values = getSizes(fileName);

        this.xValues = Collections.unmodifiableList(new ArrayList<>(values.keySet()));
        this.yValues = Collections.unmodifiableList(new ArrayList<>(values.values()));

        int forcedDegree = config.getInt("degree", 3);
        this.degree = config.getBoolean("auto", true) ? xValues.size() : forcedDegree;

        String curveFitter = config.getString("curveFitterType", "polynomial");

        AbstractCurveFitter fitter;

        switch (curveFitter) {
            case "gaussian":
                fitter = GaussianCurveFitter.create();
                break;
            case "harmonic":
                fitter = HarmonicCurveFitter.create();
                break;
            default:
                fitter = PolynomialCurveFitter.create(Math.min(degree, config.getInt("maxFuncDegree", 100)));
        }

        this.curveFitterType = curveFitter;

        this.coefficients = solve(xValues, yValues, fitter);

        this.polynomialFunction = new PolynomialFunction(this.coefficients);
    }

    private Map<Double, Double> getSizes(String fileName) throws IOException {
        String path = System.getProperty("user.dir") + "/config/" + fileName + ".json5";

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) {

            JsonReader jsonReader = new JsonReader(reader);
            Map<String, Double> temp = GSON.fromJson(jsonReader, Map.class);

            Map<Double,Double> finished = new HashMap<>();

            for(Map.Entry<String, Double> entry : temp.entrySet()) {
                finished.put(Double.parseDouble(entry.getKey()), entry.getValue());
            }

            temp.clear();

            //Not to be altered
            return Collections.unmodifiableMap(finished);
        }
    }

    private void createConfigFolder() {
        Path folderPath = Paths.get(System.getProperty("user.dir"), "config");
        if (!Files.exists(folderPath)) {
            try {
                Files.createDirectories(folderPath);
                logger.info("Config folder created!");
            } catch (IOException e) {
                throw new RuntimeException("Failed to create config folder!", e);
            }
        }
    }

    private void exportCalcFunc() {
        Path folderPath = Paths.get(System.getProperty("user.dir") + "/config/export/");

        if(!Files.exists(folderPath)) {
            try {
                Files.createDirectories(folderPath);
                logger.info("Export folder created!");
            } catch (IOException e) {
                throw new RuntimeException("Failed to create export folder!", e);
            }
        }

        Date date = new Date() ;
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss") ;

        Path exportFilePath = Paths.get(System.getProperty("user.dir") + "/config/export/" + fileName + "_func_export_" + curveFitterType + "_" + dateFormat.format(date)  + ".txt");

        if(!Files.exists(exportFilePath)) {
            try {
                Files.createFile(exportFilePath);
                logger.info("Export file created!");
            } catch (IOException e) {
                throw new RuntimeException("Failed to create export file!", e);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(exportFilePath, StandardCharsets.UTF_8)) {
            writer.write("Function Formula (Wolfram Alpha Compatible): " + polynomialFunction.toString() + "\n");
            writer.write("Function Degree: " + polynomialFunction.degree() + "\n");
            writer.write("Function Coefficients: " + Arrays.toString(coefficients) + "\n");
            writer.write("Function Values: " + values + "\n");

            writer.flush();

            logger.info("Written export file!");
        } catch (IOException e) {
            throw new RuntimeException("Failed to write export file!", e);
        }
    }
}
