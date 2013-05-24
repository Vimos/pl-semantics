package testNew; 

//Imports the standard groovy interface to PSL, the attribute similarity function interface and the database drivers
import edu.umd.cs.psl.groovy.*;
import edu.umd.cs.psl.database.RDBMS.DatabaseDriver;
import edu.umd.cs.psl.model.function.AttributeSimilarityFunction;
import edu.umd.cs.psl.config.*;

//Import this package if you want to use attribute similarity functions that come with PSL
import edu.umd.cs.psl.ui.functions.textsimilarity.*;

/* The first thing we need to do, is initialize a PSLModel which is the core component of PSL.
 * The constructor argument is the context in which the PSLModel is defined. Predicates defined
 * in the PSLModel are also automatically defined in the context.
 */

println "BUCK asd This source file is a place holder for the tree of groovy and java sources for your PSL project."

PSLModel m = new PSLModel(this);

/* We create two predicates in the model, giving their names and list of arguments. Each argument has a specified type which
 * is either Entity or Attribute.
 */
m.add predicate: "name" , person: Entity, string : Text
m.add predicate: "knows" , person1: Entity, person2 : Entity
// This additional predicate is declared as open, which means we will infer its truth values. By default, predicates are closed.
m.add predicate: "samePersons", person1: Entity, person2: Entity, open: true
// Now, we define a string similarity function bound to a predicate. Note that we can use any implementation of AttributeSimilarityFunction here!
m.add function: "sameName" , name1: Text, name2: Text, implementation: new LevenshteinStringSimilarity()//new MyStringSimilarity()//, see end of file
//new LevenshteinStringSimilarity()
/* Having added all the predicates we need to represent our problem, we finally insert some rules into the model.
 * Rules are defined using a logical syntax. Uppercase letters are variables and the predicates used in the rules below
 * are those defined above. The character '&' denotes a conjunction wheres '>>' denotes a conclusion.
 * Each rule can be given a user defined weight or no weight is specified if it is learned.
 * 
 * 'A ^ B' is a shorthand syntax for nonsymmetric(A,B), which means that in the grounding of the rule,
 * PSL does not ground the symmetric case.
 */
m.add rule : ( name(A,X) & name(B,Y) & (A ^ B) & sameName(X,Y) ) >> samePersons(A,B),  weight : 5

/* Now, we move on to defining rules with sets. Before we can use sets in rules, we have to define how we would like those sets
 * to be compared. For this we define the set comparison predicate 'sameFriends' which compares two sets of friends. For each
 * set comparison predicate, we need to specify the type of aggregator function to use, in this case its the Jaccard equality,
 * and the predicate which is used for comparison (which must be binary). Note that you can also define your own aggregator functions.
 */
m.add setcomparison: "sameFriends" , using: SetComparison.Equality, on : samePersons

/* Having defined a set comparison predicate, we can apply it in a rule. The body of the following rule is as above. However,
 * in the head, we use the 'sameFriends' set comparison to compare two sets defined using curly braces. To identify the elements
 * that are contained in the set, we can use object oriented syntax, where A.knows, denotes all those entities that are related to A
 * via the 'knows' relation, i.e the set { X | knows(A,X) }. The '+' operator denotes set union. We can also qualify a relation with
 * the 'inv' or 'inverse' keyword to denote its inverse.
 */
m.add rule :  (samePersons(A,B) & (A ^ B )) >> sameFriends( {A.knows + A.knows(inv) } , {B.knows + B.knows(inv) } ) , weight : 3.2

/* Next, we define some constraints for our model. In this case, we restrict that each person can be aligned to at most one other person
 * in the other social network. To do so, we define two partial functional constraints where the latter is on the inverse.
 */
m.add PredicateConstraint.PartialFunctional , on : samePersons
m.add PredicateConstraint.PartialInverseFunctional , on : samePersons

/* Finally, we define a prior on the inference predicate samePerson.
 */
m.add Prior.Simple, on : samePersons, weight: 1

//Let's see what our model looks like.
println m;

/* To apply our model to some dataset, we need to be able to load this dataset. PSL provides a range of convenience methods
 * for data loading and, in particular, can interact with any relational database that implements the JDBC interface.
 * So, we first setup a relational database to host the data and to store the results of the inference.
 * 
 * The DataAccess object manages all access to data.
 */
DataStore data = new RelationalDataStore(m)

// Setting up the database. Here we use the Java database H2 (www.h2database.com)
data.setup db : DatabaseDriver.H2

/* To insert data into the database, we can use insertion helpers for a specified predicate.
 * Here we show how one can manually insert data or use the insertion helpers to easily implement
 * custom data loaders.
 */
def insert = data.getInserter(name)

insert.insert(1, "John Braker");
insert.insert(2, "Mr. Jack Ressing");
insert.insert(3, "Peter Larry Smith");
insert.insert(4, "Tim Barosso");
insert.insert(5, "Jessica Pannillo");
insert.insert(8, "Peter Smithsonian");
insert.insert(9, "Miranda Parker");

insert.insert(11, "Johny Braker");
insert.insert(12, "Jack Ressing");
insert.insert(13, "PL S.");
insert.insert(14, "Tim Barosso");
insert.insert(15, "J. Panelo");
insert.insert(16, "Gustav Heinrich Gans");
insert.insert(17, "Otto v. Lautern");


// Of course, we can also load data directly from tab delimited data files.
insert = data.getInserter(knows)
def dir = 'data'+java.io.File.separator+'sn'+java.io.File.separator;
insert.loadFromFile(dir+"sn_knows.txt");

/* After having loaded the data, we are ready to run some inference and see what kind of
 * alignment our model produces. Note that for now, we are using the predefined weights.
 */
ConfigManager cm = ConfigManager.getManager();
ConfigBundle exampleBundle = cm.getBundle("example");

def result = m.mapInference(data.getDatabase(), exampleBundle)

// This prints out the results for our inference predicate
result.printAtoms(samePersons)
result.printAtoms(name)
return 

/* Next, we want to learn the weights from data. For that, we need to have some evidence
 * data from which we can learn. In our example, that means we need to specify the 'true'
 * alignment, which we now load.
 * 
 * Note, that this time we also specified a number of the insertion helper in addition to the
 * predicate. In PSL a database can be divided into partitions. By default, data is loaded
 * into the 1st partition (i.e. partition 1), but in order to distinguish different data
 * fragment, we can explicitly specify in which partition to load the data. In this case,y
 * we need to differentiate between the evidence data that will be available to PSL during
 * inference (loaded into partition 1 above), and the data that we would like to infer (loaded
 * into partition 2 below)
 */
insert = data.getInserter(samePersons,2)
insert.loadFromFileWithTruth(dir+"sn_align.txt","\t");

/*
 * We'll use the ConfigManager to access configurable properties. One place these
 * can be defined is in psl-example/src/main/resources/psl.properties
 */
//ConfigManager cm = ConfigManager.getManager();
//ConfigBundle exampleBundle = cm.getBundle("example");

/* Now, we can learn the weight, by specifying where the respective data fragments are stored
 * in the database (see above). In addition, we need to specify, which predicate we would like to
 * infer, i.e. learn on, which in our case is 'samePerson'.
 */
WeightLearningConfiguration config = new WeightLearningConfiguration();
config.setLearningType(WeightLearningConfiguration.Type.LBFGSB);
config.setInitialParameter(1.0);

m.learn data, evidence : 1, infered: 2, close : samePersons, configuration: config, config: exampleBundle

//Let's have a look at the newly learned weights.
println m

/* Now, we apply the learned model to a different social network alignment dataset. We load the 
 * dataset as before (this time into partition 5) and run inference. Finally we print the results.
 * This time, we only print those atoms with truth value greater than or equal to 0.5
 */

insert = data.getInserter(name,5);
insert.loadFromFile(dir+"sn2_names.txt");
insert = data.getInserter(knows,5);
insert.loadFromFile(dir+"sn2_knows.txt");

result = m.mapInference(data.getDatabase(parts: 5));
result.printAtoms(samePersons,true);

/**
 * This class implements the AttributeSimilarityFunction interface so that it can be used
 * as an attribute similarity function within PSL. Implementing the interface requires implementing
 * only one method which returns a number between 0 and 1 for a given pair of strings.
 *
 * This simple implementation checks whether two strings are identical, in which case it returns 1.0
 * or different (returning 0.0).
 *
 * The package edu.umd.cs.psl.model.similarityfunction.attribute contains additional and more sophisticated
 * string similarity functions of which BasicStringSimilarity is probably the most general.
 */
class MyStringSimilarity implements AttributeSimilarityFunction {
	
	@Override
	public double similarity(String a, String b) {
		return a.equals(b)?0.9:0.1;
	}
	
}
