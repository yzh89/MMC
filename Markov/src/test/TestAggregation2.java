package test;

import markov.AggregateNet;
import markov.DecisionTree;
import markov.DoubleProbability;
import markov.Machine;
import markov.Net;
import markov.Predicate;
import markov.State;
import markov.TransitionVector;

public class TestAggregation2 {
  public final static int LEVELS=6;  // levels are 0...5
  public final static int PLAYERS=3;  // players are p1...pk  w/ k = PLAYERS
  public final static String COMMONS = "commons";

  public static DecisionTree<TransitionVector<DoubleProbability>> constructTree(int startLevel) {
    /* The structure of the decision tree is going to be like:
    if p1::using=1
      if p2::using=1
        if p3::using=1 level=level-3 else level=level-2
      else
        if p3::using=1 level=level-2 else level=level-1
    else
      if p2::using=1
        if p3::using=1 level=level-2 else level=level-1
      else
        if p3::using=1 level=level-1 else level=level
    */
    return recurse(startLevel+1, 0, 1);
  }
  
  public static DecisionTree<TransitionVector<DoubleProbability>> recurse(int startLevel, int usingCount, int nextPlayer) {
    int currentLevel = startLevel-usingCount;
    if (currentLevel <= 0) {
      return makeSingleTerminal(0);
    }
    if (nextPlayer >= PLAYERS) {
      return makeBranch(nextPlayer, makeSingleTerminal(currentLevel-1), makeSingleTerminal(currentLevel));
    }
    return makeBranch(nextPlayer, recurse(startLevel, usingCount+1, nextPlayer+1), recurse(startLevel, usingCount, nextPlayer+1));
  }
  
  public static DecisionTree.Branch<TransitionVector<DoubleProbability>> makeBranch(int player, DecisionTree<TransitionVector<DoubleProbability>> cons, DecisionTree<TransitionVector<DoubleProbability>> alt) {
    Predicate pred = new Predicate.Atom("p"+player, "using", "1");
    return new DecisionTree.Branch<TransitionVector<DoubleProbability>>(pred, cons, alt);
  }
  
  public static DecisionTree.Terminal<TransitionVector<DoubleProbability>> makeSingleTerminal(int nextState) {
    TransitionVector.Builder<DoubleProbability> builder = new TransitionVector.Builder<DoubleProbability>(COMMONS);
    builder.setProbability(Integer.toString(Math.min(LEVELS-1, nextState)), DoubleProbability.ONE);
    return new DecisionTree.Terminal<TransitionVector<DoubleProbability>>(builder.build());
  }

  public static void main(String[] args) {
    try {
      long begin = System.nanoTime();

      Net.Builder<DoubleProbability> netBuilder = Net.partialParse("Commons");
      // create commons machine
      Machine.Builder<DoubleProbability> commons = new Machine.Builder<DoubleProbability>(COMMONS);
      // Commons machine will has a six states named "0" ... "5"
      for (int i = 0; i <= 5; i++) {
        State.Builder<DoubleProbability> builder = new State.Builder<DoubleProbability>(COMMONS, Integer.toString(i), constructTree(i));
        builder.setLabel("level", Integer.toString(i));
        commons.addState(builder.build());
      }
      netBuilder.addMachine(commons.build());
      
      Net<DoubleProbability> net = netBuilder.build();
      System.out.println("Parsed net");
      System.out.println(net);
      AggregateNet<DoubleProbability> aNet = new AggregateNet<DoubleProbability>(net, DoubleProbability.ZERO);
      System.out.println(aNet);
      aNet.multiply(2, 3);
      System.out.println("\nAfter multiply\n" + aNet);
      aNet.sum("p3", "using");
      System.out.println("\nAfter summing\n" + aNet);
      aNet.reduce(2);
      
      aNet.multiply(1,2);
      System.out.println("\nAfter multiply\n" + aNet);
      aNet.sum("p2", "using");
      System.out.println("\nAfter summing\n" + aNet);
      
      aNet.reduce(1);
      System.out.println("\nAfter reducing\n" + aNet);
      
      aNet.multiply(0, 1);
      System.out.println("\nAfter final multiply\n" + aNet);
      
      aNet.sum("p1","using");
      aNet.reduce(0);
      System.out.println("\nAfter final reduce\n" + aNet);
      
      double elapsed = ((double)(System.nanoTime()-begin))/1e9;
      System.out.println("\nTime taken = " + elapsed);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }
    System.exit(0);
  }
}
