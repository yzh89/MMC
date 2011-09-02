package markov;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import parser.XmlParser;
import util.Ptr;
import util.Stack;

public class Simulation {
  
  public TreeMap<String,Integer> headlist;
  public TransitionMatrix<FractionProbability> matrix;
  
  public static void main(String[] args) {
    System.out.println(new Simulation("xml/umlVersion3.xml"));
  }

  public Simulation(String xmlFileName){
      Net<FractionProbability> net=XmlParser.XmlInput(xmlFileName);

      Iterator<Machine<FractionProbability>> itr=net.iterator();
      
      /****** Initialization ***********************************************************
       * 1. Retrieve stateNameList to assign combined state a count up number to be used in transition Matrix mapping  -->cannot be initialized here since machine name is not compared
       * 2. Get a iterator array to save all the pointer to state of each machine --> change to 3
       * 3. Initialize stack with every machines to be used in recursive combined state retrieval
       * 4. Get a list of machine names
       * **************************************************************************/
      HashMap<String,Integer> stateNameList=new HashMap<String, Integer>();
//      ArrayList<Iterator<State<FractionProbability>>> itrStateArray=new ArrayList<Iterator<State<FractionProbability>>>();
      Stack<Machine<FractionProbability>> stack= Stack.<Machine<FractionProbability>>emptyInstance();
      ArrayList<State<FractionProbability>> stateArray=new ArrayList<State<FractionProbability>>();
      HashSet<ArrayList<State<FractionProbability>>> accu=new HashSet<ArrayList<State<FractionProbability>>>();
      ArrayList<String> machineName=new ArrayList<String>();
      
      Machine<FractionProbability>tempB=null;
      Machine<FractionProbability>tempA=itr.next();
      stack=stack.push(tempA);
      machineName.add(tempA.name);
//      itrStateArray.add(tempA.iterator());
      
      while (itr.hasNext()){
        tempB=itr.next();
//        initializeStates(tempA,tempB,stateNameList);
//        itrStateArray.add(tempB.iterator());
        stack=stack.push(tempB);
        machineName.add(tempB.name);
        tempA=tempB;
      }
      
      
      constructStateList(stateArray,stack,accu);
      
      TransitionMatrix.RandomBuilder<FractionProbability> builder =new TransitionMatrix.RandomBuilder<FractionProbability>(stateNameList.size());
      
      Iterator<ArrayList<State<FractionProbability>>> itrStates=accu.iterator();
      while(itrStates.hasNext()){
        int rowNum=-1;
        ArrayList<FractionProbability> row=retrieveProbability(machineName,(itrStates.next()),net.dictionary, stateNameList, rowNum);
        builder.set(rowNum, row);
      }

      this.matrix = builder.build();
      
      ValueComparator bvc =  new ValueComparator(stateNameList);
      this.headlist = new TreeMap<String, Integer>(bvc);
      headlist.putAll(stateNameList);

      System.out.println(headlist.keySet().toString());
      System.out.println(matrix.toString()); 
    

  }
  
  private ArrayList<FractionProbability> retrieveProbability(
      ArrayList<String> machineName,
      ArrayList<State<FractionProbability>> states, Dictionary dictionary, HashMap<String, Integer> stateNameList, int rowNum) {
    

    
    ArrayList<Ptr<TransitionVector<FractionProbability>>> ptrs=new ArrayList<Ptr<TransitionVector<FractionProbability>>>();
    TransitionVector<FractionProbability> out=null;
    String combinedStateName="empty";
    
    for (int machineCounter=0;machineCounter<machineName.size();machineCounter++){
      ArrayList<State<FractionProbability>> stateCopy=(ArrayList<State<FractionProbability>>) states.clone();
      ArrayList<String> machineNameCopy=(ArrayList<String>) machineName.clone();
      
      State<FractionProbability> Astate =states.get(machineCounter);
      DecisionTree<TransitionVector<FractionProbability>> decisionTreeA=Astate.getTransitionFunction();
      Evaluation<TransitionVector<FractionProbability>> eva=new Evaluation<TransitionVector<FractionProbability>>(dictionary,decisionTreeA);
      Evaluation<TransitionVector<FractionProbability>>.Evaluator root=eva.root;
      
      stateCopy.remove(machineCounter);
      machineNameCopy.remove(machineCounter);
    
      for(int i=0;i<stateCopy.size();i++){
        State<FractionProbability> s=stateCopy.get(i);
        Iterator<String> itrLabel=s.labelNameIterator();
        while(itrLabel.hasNext()){
          String labelName=itrLabel.next();
          String instance=s.getLabel(labelName);
          Predicate.Atom atom=new Predicate.Atom(machineName.get(i),labelName,instance);
          root=root.restrict(atom);      
        }
      }
      
      final Ptr<TransitionVector<FractionProbability>> ptr=new Ptr<TransitionVector<FractionProbability>>();
      root.accept(new Evaluation.Visitor<TransitionVector<FractionProbability>>() {

        public void visitTerminal(Evaluation<TransitionVector<FractionProbability>>.Terminal term) {
          ptr.value=term.output;
        }

        public void visitWalker(Evaluation<TransitionVector<FractionProbability>>.Walker walker) {
          ptr.value=null;
          System.err.println("End up in walker!!");
        }
      });
      // assign TransitionVector 
      out=(out==null) ? ptr.value : out.times(ptr.value); 
      ptrs.add(ptr);
    }
    //assign combined state name
    String machineTemp=out.machineName;
    String[] orderedMachine=machineTemp.split(" X ");
    for (String s:orderedMachine){
      combinedStateName=(combinedStateName.equals("empty")) ? states.get(machineName.indexOf(s)).name : combinedStateName+Machine.MULTIPLY_STRING+states.get(machineName.indexOf(s)).name;
    }
    
    rowNum=stateNameList.get(combinedStateName);
    
    Iterator<Map.Entry<String, FractionProbability>> itrMergedLabel=out.iterator();
    ArrayList<FractionProbability> row=new ArrayList<FractionProbability>();
    
    for (int i=0;i<states.size();i++){
      row.add(i, FractionProbability.ZERO);
    }
    
    while(itrMergedLabel.hasNext()){
      Entry<String, FractionProbability> temp = itrMergedLabel.next();
      int colNum=(stateNameList.get(temp.getKey())!=null)?stateNameList.get(temp.getKey()):-1;
      if (colNum>-1) row.set(colNum, temp.getValue());
    }
    
    return row;
  }

  private void constructStateList(
      ArrayList<State<FractionProbability>> stateArray,
      Stack<Machine<FractionProbability>> stack, HashSet<ArrayList<State<FractionProbability>>> accu) {
    //output: accu is HashSet which every element is ArrayList of stateArray
    if (stack.isEmpty()){
      accu.add(stateArray);
      return;
    }
    Iterator<State<FractionProbability>> itr=stack.head().iterator();
    while(itr.hasNext()){
      State<FractionProbability> s=itr.next();
      ArrayList<State<FractionProbability>> stateArrayTemp = new ArrayList<State<FractionProbability>>(stateArray);
      stateArrayTemp.add(s);
      constructStateList(stateArrayTemp,stack.tail(),accu);
    }

  }
  
  private void initializeStates(
      Machine<FractionProbability> A,
      Machine<FractionProbability> B,
      HashMap<String, Integer> stateNameList) {
    HashMap<String, Integer> out=new HashMap<String, Integer>();
    
    if (!stateNameList.isEmpty()){

      Iterator<State<FractionProbability>> itrBState= B.iterator();
      int count=0;

      while (itrBState.hasNext()){
        State<FractionProbability> tempB = itrBState.next();
        Iterator<String> itrAState=stateNameList.keySet().iterator();
        while(itrAState.hasNext()){
          String tempAName = itrAState.next();
          out.put((tempAName+Machine.MULTIPLY_STRING+tempB.name),new Integer(count));
          count++;
        }       
      }
    
      stateNameList.clear();
      stateNameList.putAll(out);
      return;
    }
    else {

      Iterator<State<FractionProbability>> itrBState= B.iterator();
      int count=0;

      while (itrBState.hasNext()){
        State<FractionProbability> tempB = itrBState.next();
        Iterator<State<FractionProbability>> itrAState=A.iterator();
      
        while(itrAState.hasNext()){
          
          State<FractionProbability> tempA = itrAState.next();
          stateNameList.put((tempA.name+Machine.MULTIPLY_STRING+tempB.name),new Integer(count));
          count++;
        }       
      }
    return;
    }
  
  }
    
  class ValueComparator implements Comparator<String>{

    Map<String, Integer> base;
    public ValueComparator(Map<String, Integer> base) {
        this.base = base;
    }

    public int compare(String key1, String key2) {
      if((Integer)base.get(key1) < (Integer)base.get(key2)) {
        return -1;
      } else if((Integer)base.get(key1) == (Integer)base.get(key2)) {
        return 0;
      } else {
        return 1;
      }

    }
  }

}