package edu.ttu.hothienlac;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import cern.colt.Arrays;
import edu.ttu.hothienlac.HMapSIW;
import tl.lin.data.pair.PairOfStrings;


public class StripesPMI extends Configured implements Tool {

  private static final Logger LOG = Logger.getLogger(StripesPMI.class);

  // First stage Mapper: emits pairs (token, 1) for each unique token in an input value,
  //                  and a pair (token pair, 1) for each unique pair of tokens
  private static class AppearanceCountMapper extends Mapper<LongWritable, Text, Text, IntWritable> {

    // Objects for reuse
    private final static Text KEY = new Text();
    private final static IntWritable ONE = new IntWritable(1);

    @Override
    public void map(LongWritable key, Text value, Context context)
        throws IOException, InterruptedException{

      String line = value.toString();
      StringTokenizer t = new StringTokenizer(line);
      Set<String> unique = new HashSet<String>();
      String token = "";

      while(t.hasMoreTokens()){
        token = t.nextToken();

        if(unique.add(token)){
          KEY.set(token);
          context.write(KEY, ONE);
        }
      }

    }
  }

  // First stage Reducer: Totals counts for each Token
  private static class AppearanceCountReducer extends Reducer<Text, IntWritable, Text, IntWritable> {
    // Reuse objects
    private final static IntWritable SUM = new IntWritable();

    @Override
    public void reduce(Text key, Iterable<IntWritable> values, Context context)
        throws IOException, InterruptedException{
      int sum = 0;
      for(IntWritable value : values){
        sum += value.get();
      }

      SUM.set(sum);
      context.write(key, SUM);
    }
  }



  // Second stage mapper: Maps key: term, value: map term to "cooccurance" neighbors 
  private static class StripesPMIMapper extends Mapper<LongWritable, Text, Text, HMapSIW> {

    // Objects for reuse
    private final static Text KEY = new Text();
    private final static HMapSIW MAP = new HMapSIW();

    @Override
    public void map(LongWritable key, Text value, Context context) 
        throws IOException, InterruptedException{

      String line = value.toString();
      StringTokenizer t = new StringTokenizer(line);

      // Need to pass through multiple times so put tokens into array
      List<String> terms = new ArrayList<String>();
      while(t.hasMoreTokens()){
        terms.add(t.nextToken());
      }


      String left = "";
      String right = "";
      for(int leftTermIndex = 0; leftTermIndex < terms.size(); leftTermIndex++){
        left = terms.get(leftTermIndex);

        for(int rightTermIndex = leftTermIndex + 1; rightTermIndex < terms.size(); rightTermIndex++) {
          right = terms.get(rightTermIndex);

          if(!MAP.containsKey(right)){
            MAP.put(right, 1);
          }

        } // Each right word put in map


        KEY.set(left);
        context.write(KEY, MAP);
        MAP.clear();
      }


    }
  }

  //TODO Write fill in combiner boilerplate
  //Combiner
  private static class StripesPMICombiner extends Reducer<Text, HMapSIW, Text, HMapSIW> {
    private static HMapSIW MAP = new HMapSIW(); 



    @Override
    public void reduce(Text term, Iterable<HMapSIW> values, Context context) 
        throws IOException, InterruptedException{

      // Do a element-wise sum of the maps
      for(HMapSIW pairMap : values){

        for(String key : pairMap.keySet()){

          if(MAP.containsKey(key)){
            MAP.put(key, MAP.get(key) + pairMap.get(key));
          }else{
            MAP.put(key, pairMap.get(key));
          }

        }

      }
      context.write(term, MAP);
    }
  }

  // Second Stage reducer: Finalizes PMI Calculation given 
  private static class StripesPMIReducer extends Reducer<Text, HMapSIW, PairOfStrings, DoubleWritable> {

    private static Map<String, Integer> termTotals = new HashMap<String, Integer>();
    private static Map<String, Integer> MAP = new HashMap<String, Integer>();
    private static PairOfStrings PAIR = new PairOfStrings();
    private static DoubleWritable PMI = new DoubleWritable();
    private static double totalDocs = 156215;

    @Override
    public void setup(Context context) throws IOException{
      //TODO Read from intermediate output of first job
      // and build in-memory map of terms to their individual totals
      Configuration conf = context.getConfiguration();
      FileSystem fs = FileSystem.get(conf);

      //      Path inFile = new Path(conf.get("intermediatePath"));
      Path inFile = new Path("/Users/chris/Projects/UMD/MapReduce-assignments/assignment2/appearance_totals_stripes/part-r-00000");

      if(!fs.exists(inFile)){
        throw new IOException("File Not Found: " + inFile.toString());
      }

      BufferedReader reader = null;
      try{
        FSDataInputStream in = fs.open(inFile);
        InputStreamReader inStream = new InputStreamReader(in);
        reader = new BufferedReader(inStream);

      } catch(FileNotFoundException e){
        throw new IOException("Exception thrown when trying to open file.");
      }


      String line = reader.readLine();
      while(line != null){

        String[] parts = line.split("\\s+");
        if(parts.length != 2){
          LOG.info("Input line did not have exactly 2 tokens: '" + line + "'");
        } else {
          termTotals.put(parts[0], Integer.parseInt(parts[1]));
        }
        line = reader.readLine();
      }

      reader.close();

    }

    @Override
    public void reduce(Text term, Iterable<HMapSIW> values, Context context ) 
        throws IOException, InterruptedException{
      // Recieving pair and pair counts -> Sum these for this pair's total
      // Only calculate PMI for pairs that occur 10 or more times


      // Do a element-wise sum of the maps
      for(HMapSIW pairMap : values){

        for(String key : pairMap.keySet()){

          if(MAP.containsKey(key)){
            MAP.put(key, MAP.get(key) + pairMap.get(key));
          }else{
            MAP.put(key, pairMap.get(key));
          }
        }
      }

      // MAP contians the total co-appearnaces for incoming key, "term" 
      //    and all the keys of the map. We'll make Pairs like this: (term, key_i)
      String leftTerm = term.toString();

      for(String key : MAP.keySet()){
        PAIR.set(leftTerm, key);

        double probPair = MAP.get(key) / totalDocs;
        double probLeft = termTotals.get(leftTerm) / totalDocs;
        double probRight = termTotals.get(key) / totalDocs;

        double pmi = Math.log(probPair / (probLeft * probRight));

        PMI.set(pmi);
        context.write(PAIR, PMI);

      }
      
      MAP.clear();

    }
    
  }






  public StripesPMI() {}

  private static final String INPUT = "input";
  private static final String OUTPUT = "output";
  private static final String NUM_REDUCERS = "numReducers";

  @SuppressWarnings("static-access")
  public int run(String[] args) throws Exception {
    Options options = new Options();

    options.addOption(OptionBuilder.withArgName("path").hasArg()
        .withDescription("input path").create(INPUT));
    options.addOption(OptionBuilder.withArgName("path").hasArg()
        .withDescription("output path").create(OUTPUT));
    options.addOption(OptionBuilder.withArgName("num").hasArg()
        .withDescription("number of reducers").create(NUM_REDUCERS));

    CommandLine cmdline;
    CommandLineParser parser = new GnuParser();

    try{
      cmdline = parser.parse(options, args);
    } catch (ParseException exp) {
      System.err.println("Error parsing command line: " + exp.getMessage());
      return -1;
    }

    if(!cmdline.hasOption(INPUT) || !cmdline.hasOption(OUTPUT)) {
      System.out.println("args: " + Arrays.toString(args));
      HelpFormatter formatter = new HelpFormatter();
      formatter.setWidth(120);
      formatter.printHelp(this.getClass().getName(), options);
      ToolRunner.printGenericCommandUsage(System.out);
      return -1;
    }

    String inputPath = cmdline.getOptionValue(INPUT);

    //TODO This output path is for the 2nd job's.
    //    The fits job will have an intermediate output path from which the second job's reducer will read
    String outputPath = cmdline.getOptionValue(OUTPUT);
    String intermediatePath = "/Users/chris/Projects/UMD/MapReduce-assignments/assignment2/appearance_totals_stripes";




    int reduceTasks = cmdline.hasOption(NUM_REDUCERS) ? 
        Integer.parseInt(cmdline.getOptionValue(NUM_REDUCERS)) : 1;

        LOG.info("Tool: " + StripesPMI.class.getSimpleName() + " Appearances Count");
        LOG.info(" - input path: " + inputPath);
        LOG.info(" - output path: " + intermediatePath);
        LOG.info(" - number of reducers: " + reduceTasks);

        Configuration conf = getConf();
        conf.set("intermediatePath", intermediatePath);

        Job job1 = Job.getInstance(conf);
        job1.setJobName(StripesPMI.class.getSimpleName() + " Appearance Count");
        job1.setJarByClass(StripesPMI.class);

        job1.setNumReduceTasks(reduceTasks);

        FileInputFormat.setInputPaths(job1, new Path(inputPath));
        FileOutputFormat.setOutputPath(job1, new Path(intermediatePath));

        job1.setOutputKeyClass(Text.class);
        job1.setOutputValueClass(IntWritable.class);

        job1.setMapperClass(AppearanceCountMapper.class);
        job1.setCombinerClass(AppearanceCountReducer.class);
        job1.setReducerClass(AppearanceCountReducer.class);

        // Delete the output directory if it exists already.
        Path intermediateDir = new Path(intermediatePath);
        FileSystem.get(conf).delete(intermediateDir, true);

        long startTime = System.currentTimeMillis();
        job1.waitForCompletion(true);
        LOG.info("Job Finished in " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds");


        // Start second job
        LOG.info("Tool: " + StripesPMI.class.getSimpleName() + " Stripes PMI Part");
        LOG.info(" - input path: " + inputPath);
        LOG.info(" - output path: " + outputPath);
        LOG.info(" - number of reducers: " + reduceTasks);

        Job job2 = Job.getInstance(conf);
        job2.setJobName(StripesPMI.class.getSimpleName() + " StripesPMICalcuation");
        job2.setJarByClass(StripesPMI.class);

        job2.setNumReduceTasks(reduceTasks);

        FileInputFormat.setInputPaths(job2,  new Path(inputPath));
        TextOutputFormat.setOutputPath(job2, new Path(outputPath));

        //TODO Which output key??
        job2.setOutputKeyClass(Text.class);
        job2.setOutputValueClass(HMapSIW.class);
        job2.setMapperClass(StripesPMIMapper.class);
        job2.setCombinerClass(StripesPMICombiner.class);
        job2.setReducerClass(StripesPMIReducer.class);

        Path outputDir = new Path(outputPath);
        FileSystem.get(conf).delete(outputDir, true);

        startTime = System.currentTimeMillis();
        job2.waitForCompletion(true);
        LOG.info("Job Finished in " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds");


        return 0;
  }

  /**
   * @param args
   */
  public static void main(String[] args) throws Exception{
    ToolRunner.run(new StripesPMI(), args);
  }
}