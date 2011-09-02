package test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.TreeSet;
import java.util.Formatter;

import markov.Alphabet;
import markov.Constraint;
import markov.Dictionary;
import markov.Domain;
import markov.Predicate;
import markov.Predicate.CollectionType;
import markov.Romdd;

import util.Coroutine;
import util.Pair;
import util.Partition;
import util.Stack;
import util.TerminatedIterator;

public class Sudoku {
  public final int N;
  public final int order;

  private int[] puzzle;
  private int[] solution;
  
  public static void main(String[] args) throws IOException { 
    Sudoku puzzle = parse(new BufferedReader(new FileReader(args[0])));
    Solver solver = new Solver(puzzle);
    solver.altSolve();
    System.out.println(puzzle);
    System.out.println("Verified = " + puzzle.verifySolution());
    System.exit(0);
  }
  
  public static Sudoku parse(BufferedReader reader) throws IOException {
    String nStr = reader.readLine();
    int order = Integer.parseInt(nStr);
    int N = order*order;
    Builder builder = new Builder(order);
    for (int i = 0; i < N; i++) {
      String[] nums = reader.readLine().split("\\s+");
      for (int j = 0; j < N; j++) {
        int val = Integer.parseInt(nums[j]);
        if (val != 0) builder.set(i, j, val);
      }
    }
    return builder.build();
  }
  
  private Sudoku(int order, int[] puzzle) {
    this.N = order*order;
    this.order = order;
    this.puzzle = new int[N*N];
    System.arraycopy(puzzle, 0, this.puzzle, 0, N*N);
    this.solution = new int[N*N];
    System.arraycopy(puzzle, 0, this.solution, 0, N*N);
  }
  
  public int get(int row, int col) {
    if (row < 0 || row >= N || col < 0 || col >= N) throw new ArrayIndexOutOfBoundsException();
    return puzzle[row*N+col];
  }
  
  public TerminatedIterator<Entry> nonZeroIterator() {
    return new Coroutine<Entry>() {
      public void init() {
        for (int row = 0; row < N; row++) {
          for (int col = 0; col < N; col++) {
            int val = puzzle[N*row + col];
            if (val != 0) yield(new Entry(row, col, val));
          }
        }
      }
    }.iterator();
  }
  
  public int get(Pair<Integer> pair) {
    return get(pair.first, pair.second);
  }
  
  public int get(int idx) {
    return puzzle[idx];
  }
  
  public void setSolution(int row, int col, int val) {
    if (row < 0 || row >= N || col < 0 || col >= N) throw new ArrayIndexOutOfBoundsException();
    solution[N*row+col] = val;
  }
  
  public void setSolution(Pair<Integer> coord, int val) {
    setSolution(coord.first, coord.second, val);
  }
  
  public void setSolution(Entry e) {
    setSolution(e.row, e.col, e.val);
  }
  
  public int getSolution(int row, int col) {
    if (row < 0 || row >= N || col < 0 || col >= N) throw new ArrayIndexOutOfBoundsException();
    return solution[N*row+col];
  }
  
  public int getSolution(Pair<Integer> coord) {
    return getSolution(coord.first, coord.second);
  }
  
  public List<Pair<Integer>> getNeighbors(Pair<Integer> pair) {
    return getNeighbors(pair.first, pair.second);
  }
  
  public List<Pair<Integer>> getNeighbors(int row, int col) {
    ArrayList<Pair<Integer>> neighbors = new ArrayList<Pair<Integer>>();
    Pair<Integer> blockInfo = fromIdxToBlockIdx(N, order, row, col);
    for (int i = 0; i < N; i++) {
      if (i != row) neighbors.add(new Pair<Integer>(i, col));
      if (i != col) neighbors.add(new Pair<Integer>(row, i));
      if (i != blockInfo.second) neighbors.add(fromBlockIdxToIdx(N, order, blockInfo.first, i));
    }
    return neighbors;
  }
  
  /** Blocks are indexed by block number, offset. Example:
   *  1 | 2  There are sqrt(N) x sqrt(N) blocks labeled thusly.
   * -------  
   *  3 | 4  Each block is internally structured just like overall block structure.
   * */
  public static Pair<Integer> fromIdxToBlockIdx(int N, int root, int row, int col) {
    int blockRow = row / root;
    int blockRowOffset = row % root;
    int blockCol = col / root;
    int blockColOffset = col % root;

    int blockNum = blockRow*root + blockCol;
    int blockOffset = blockRowOffset * root + blockColOffset;
    
    return new Pair<Integer>(blockNum, blockOffset);
  }
  
  public static Pair<Integer> fromBlockIdxToIdx(int N, int root, int blockNum, int offset) {
    int blockRow = blockNum / root;
    int blockCol = blockNum % root;
    int offRow = offset / root;
    int offCol = offset % root;
    
    return new Pair<Integer>(blockRow * root + offRow, blockCol*root + offCol);
  }
  
  public boolean verifySolution() {
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        int val = solution[N*i + j];
        for (Pair<Integer> neighbor : getNeighbors(i, j)) {
          if (val == getSolution(neighbor)) return false;
        }
      }
    }
    return true;
  }
  
  public String toString() {
    Formatter format = new Formatter();
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        format.format("%4d", solution[N*i+j]);
      }
      format.format("\n");
    }
    return format.toString();
  }
  
  public static class Builder {
    public final int N;
    public final int order;
    private int[] puzzle;

    public Builder(int order) {
      this.order = order;
      this.N = order*order;
      puzzle = new int[N*N];
    }

    public void set(int row, int col, int value) {
      if (row < 0 || row >= N || col < 0 || col >= N) throw new ArrayIndexOutOfBoundsException();
      if (value < 1 || value > N) throw new RuntimeException();
      
      puzzle[row*N+col] = value;
    }
    
    public void validate() throws ValidationException {
      BitSet tracker1 = new BitSet(N);  // checks rows
      BitSet tracker2 = new BitSet(N);  // checks cols
      BitSet tracker3 = new BitSet(N);  // checks neighborhoods
      for (int row = 0; row < N; row++) {
        tracker1.clear();
        tracker2.clear();
        tracker3.clear();
        for (int col = 0; col < N; col++) {
          int val = puzzle[row*N+col];
          if (val != 0) {
            if (tracker1.get(val)) throw new RuntimeException("Invalid puzzle.");
            tracker1.set(val);
          }
          val = puzzle[col*N+row];
          if (val != 0) {
            if (tracker2.get(val)) throw new RuntimeException("Invalid puzzle.");
            tracker2.set(val);
          }
          Pair<Integer> pair = fromBlockIdxToIdx(N, order, row, col);
          val = puzzle[pair.first*N + pair.second];
          if (val != 0) {
            if (tracker3.get(val)) throw new RuntimeException("Invalid puzzle.");
            tracker3.set(val);            
          }
        }
      }
    }

    public Sudoku build() throws ValidationException {
      validate();
      return new Sudoku(order, puzzle);
    }
  }
  
  public static class ValidationException extends RuntimeException {
    private static final long serialVersionUID = -4291372421542506125L;}
    
  public static class SolverException extends RuntimeException {
    private static final long serialVersionUID = -397675822428486153L;
    public SolverException() { }
    public SolverException(String msg) { super(msg); }
  }

  public static class Entry {
    public final String name;
    public final int row;
    public final int col;
    public final int val;
    
    public Entry(int row, int col, int val) {
      this.row = row;
      this.col = col;
      this.val = val;
      this.name = row + "." + col;
    }
    
    public Entry(String name, int val) {
      this.name = name;
      this.val = val;
      String[] names = name.split("\\.");
      row = Integer.parseInt(names[0]);
      col = Integer.parseInt(names[1]);
    }
    
    public Entry(Pair<Integer> pair, int val) {
      this(pair.first, pair.second, val);
    }
    
    public Entry(Pair<Integer> pair, String next) {
      this(pair, Integer.parseInt(next));
    }

    public String toString() {
      return name + " = " + val;
    }
  }
  
  public static Pair<Integer> toPair(String name) {
    String[] names = name.split("\\.");
    return new Pair<Integer>(Integer.parseInt(names[0]),Integer.parseInt(names[1]));  
  }

  public boolean isNeighbor(Pair<Integer> p1, Pair<Integer> p2) {
    if (p1.first.equals(p2.first) || p1.second.equals(p2.second)) return true;
    if (p1.first / order == p2.first / order && p1.second / order == p2.second) return true; 
    return false;
  }
  
  public static class Solver {
    public final int N;
    public final Sudoku instance;
    public final Dictionary dict;

    boolean[] marked;
    int markCount;
    TreeSet<Pair<Integer>> tapped;
    Stack<Romdd<Boolean>> stack;
    Stack<Romdd<Boolean>> backStack;
    
    public Solver(Sudoku instance) {
      this.N = instance.N;
      this.instance = instance;
      this.dict = buildDictionary();
      marked = new boolean[N*N];
      initializeMarkings();
      tapped = new TreeSet<Pair<Integer>>();
      backStack = Stack.<Romdd<Boolean>>emptyInstance();
      stack = backStack.push(Romdd.TRUE);
    }
    
    public void altSolve() {
      System.out.println("In alt solver.");
      Romdd<Boolean> constraints = buildInitialConstraints();
      System.out.println("Initial constraints complete.");

      while (Romdd.TRUE.compareTo(constraints) != 0) {
        System.out.println("Computing value tables");
        Partition<ValueTable> tables = getValueTable(constraints);
        System.out.println("Value tables computed");
        if (tables.get(0).values.size() == 1) {
          System.out.println("Found block.");
          for (ValueTable table : tables.getBlock(0)) {
            Entry fixed = new Entry(table.pair, table.values.iterator().next());
            System.out.println(fixed);
            instance.setSolution(fixed);
            setMarked(fixed.row, fixed.col);
  
            constraints = constraints.restrict(fixed.row + "." + fixed.col, Integer.toString(fixed.val));
  
            Predicate.CollectionBuilder pBuilder = new Predicate.CollectionBuilder(CollectionType.AND);         
            for (Pair<Integer> neighbor : instance.getNeighbors(fixed.row, fixed.col)) {
              if (isMarked(neighbor)) continue;
              pBuilder.add(new Predicate.Neg(buildAtom(neighbor, fixed.val)));
            }
            if (pBuilder.size() >= 1) {
              constraints = Romdd.<Boolean>apply(Romdd.AND, constraints, pBuilder.build().toRomdd(dict));
            }
          }
          tapped.clear();
        } else {
          System.out.println("Block not found.");
          ValueTable table = null;
          boolean found = false;
          for (int next = 0; !found && next < tables.size(); ) {
            table = tables.get(next);
            if (tapped.contains(table.pair)) next++;
            else found = true;
          }
          if (!found) throw new SolverException();
          tapped.add(table.pair);
          
          System.out.println("Trying table " + table);
          
          Predicate.CollectionBuilder pBuilder = new Predicate.CollectionBuilder(CollectionType.AND);
          for (Pair<Integer> neighbor : instance.getNeighbors(table.pair)) {
            if (isMarked(neighbor)) continue;
            for (String val : table.values) {
              pBuilder.add(new Predicate.Neg(new Predicate.And(buildAtom(neighbor, val), buildAtom(neighbor, val))));
            }
          }
          // System.out.println(constraints.listVarNames());
        }
      }
    }
    
    Romdd<Boolean> buildInitialConstraints() {
      Predicate.CollectionBuilder pBuilder = new Predicate.CollectionBuilder(CollectionType.AND);
      TerminatedIterator<Entry> it = instance.nonZeroIterator();
      while (it.hasNext()) {
        Entry entry = it.next();
        for (Pair<Integer> neighbor : instance.getNeighbors(entry.row, entry.col)) {
          if (isMarked(neighbor)) continue;
          pBuilder.add(new Predicate.Neg(buildAtom(neighbor, entry.val)));
        }
      }
      return pBuilder.build().toRomdd(dict);      
    }
    
    TerminatedIterator<Pair<Integer>> unmarkedIterator() {
      return new Coroutine<Pair<Integer>>() {
        public void init() {
          for (int row = 0; row < N; row++) {
            for (int col = 0; col < N; col++) {
              if (isMarked(row, col)) continue;
              yield(new Pair<Integer>(row, col));
            }
          }
        }        
      }.iterator();
    }
    
    private Predicate.Atom buildAtom(Pair<Integer> pair, int val) {
      return buildAtom(pair.first, pair.second, val);
    }
    
    private Predicate.Atom buildAtom(Pair<Integer> pair, String val) {
      return new Predicate.Atom(Integer.toString(pair.first), Integer.toString(pair.second), val);
    }
    
    private Predicate.Atom buildAtom(int row, int col, int val) {
      return new Predicate.Atom(Integer.toString(row), Integer.toString(col), Integer.toString(val));
    }
    
    ArrayList<Constraint> createConstraints() {
      ArrayList<Constraint> rv = new ArrayList<Constraint>();
      // create row, col, block constraints
      for (int idx = 0; idx < N; idx++) {
        Romdd<Boolean> rowConstraint = Romdd.TRUE, colConstraint = Romdd.TRUE, blockConstraint = Romdd.TRUE;
        for (int c1 = 0; c1 < N; c1++) {
          for (int c2 = c1+1; c2 < N; c2++) {
            Predicate p = createPairPredicate(new Pair<Integer>(idx, c1), new Pair<Integer>(idx, c2));
            rowConstraint = Romdd.apply(Romdd.AND, rowConstraint, p.toRomdd(dict));
            p = createPairPredicate(new Pair<Integer>(c1, idx), new Pair<Integer>(c2, idx));
            colConstraint = Romdd.apply(Romdd.AND, colConstraint, p.toRomdd(dict));
            p = createPairPredicate(fromBlockIdxToIdx(N, instance.order, idx, c1), fromBlockIdxToIdx(N, instance.order, idx, c2));
            blockConstraint = Romdd.apply(Romdd.AND, blockConstraint, p.toRomdd(dict));
          }
        }
        rv.add(new Constraint(rowConstraint));
        rv.add(new Constraint(colConstraint));
        rv.add(new Constraint(blockConstraint));
      }
      return rv;
    }
    
    Predicate createPairPredicate(Pair<Integer> p1, Pair<Integer> p2) {
      if (instance.get(p1) != 0) {
        if (instance.get(p2) != 0) {
          return null;
        } else {
          return new Predicate.Neg(new Predicate.Atom(Integer.toString(p2.first), Integer.toString(p2.second), Integer.toString(instance.get(p1))));
        }
      } else if (instance.get(p2) != 0) {
        return new Predicate.Neg(new Predicate.Atom(Integer.toString(p1.first), Integer.toString(p1.second), Integer.toString(instance.get(p2))));
      } else {
        Predicate.CollectionBuilder builder = new Predicate.CollectionBuilder(CollectionType.AND);
        for (int i = 1; i <= N; i++) {
          Predicate.CollectionBuilder termBuilder = new Predicate.CollectionBuilder(CollectionType.AND);
          termBuilder.add(new Predicate.Atom(Integer.toString(p1.first), Integer.toString(p1.second), Integer.toString(i)));
          termBuilder.add(new Predicate.Atom(Integer.toString(p2.first), Integer.toString(p2.second), Integer.toString(i)));
          builder.add(new Predicate.Neg(termBuilder.build()));
        }
        return builder.build();
      }
    }

    public void solve() throws SolverException {
      while (markCount < N*N-1) {  // we should be able to read the value of the last node
        Entry fixed = findDetermined(stack.head());
        if (fixed != null) {
System.out.println("The value of " + fixed.row + ", " + fixed.col + " was determined to be " + fixed.val);
          eliminate(fixed.row, fixed.col);
        } else {
          Pair<Integer> opt = findBestCandidate();
          eliminate(opt.first, opt.second);
        }
      }
      System.out.println("Forward propagation complete. Beginning back-substitution.");
      
      TreeSet<String> firstNames = stack.head().listVarNames();
      if (firstNames.size() != 1) throw new RuntimeException();
      String firstName = firstNames.iterator().next();      
      TreeSet<String> answer = stack.head().findChildrenReaching(firstName, true);
      if (answer.size() != 1) throw new RuntimeException();
      int firstVal = Integer.parseInt(answer.iterator().next());

      ArrayList<Entry> recorded = new ArrayList<Entry>();
      Entry firstEntry = new Entry(firstName, firstVal);
      instance.setSolution(firstEntry);
      recorded.add(firstEntry);
      // back substitute
      Stack<Romdd<Boolean>> next = backStack;
      while (!next.isEmpty()) {
        Romdd<Boolean> equation = next.head();
        System.out.println("Examining equation with vars " + equation.listVarNames());
        // TestRomdd.printTableOfReachableValues(equation);
        for (Entry e : recorded) {
          System.out.println("Back-subtituting " + e);
          equation = equation.restrict(e.name, Integer.toString(e.val));
//          TestRomdd.printTableOfReachableValues(equation);
        }
        TreeSet<String> vars = equation.listVarNames();

        if (vars.size() != 1) throw new RuntimeException();  // this should never happen
        String varName = vars.iterator().next();
        
        TreeSet<String> solutions = equation.findChildrenReaching(varName, true);
        if (solutions.size() != 1) {
          throw new SolverException("Puzzle appears " + (solutions.size() < 1 ? "overconstrained."
              : "underconstrained: >=" + solutions.size() + " solutions exist."));
        }
        Entry newEntry = new Entry(varName, Integer.parseInt(solutions.iterator().next()));
        instance.setSolution(newEntry);
        recorded.add(newEntry);
        
        next = next.tail();
      }
    }

    // Instantiates all neighboring constraints and then eliminates the given variable.
    public void eliminate(int row, int col) {
      if (isMarked(row, col)) throw new RuntimeException();
System.out.println("Eliminating " + row + ", " + col);
      
      Predicate.CollectionBuilder builder = new Predicate.CollectionBuilder(CollectionType.AND);
      for (Pair<Integer> neighbor : instance.getNeighbors(row, col)) {
        int assigned = instance.get(neighbor);
        if (assigned != 0) {
          // instantiated constraints
          builder.add(new Predicate.Neg(new Predicate.Atom(Integer.toString(row), Integer.toString(col), Integer.toString(assigned))));          
        } else if (!isMarked(neighbor)){
          // conventional handling of constraints
          for (int i = 1; i <= N; i++) {
            Predicate.CollectionBuilder termBuilder = new Predicate.CollectionBuilder(CollectionType.AND);
            termBuilder.add(new Predicate.Atom(Integer.toString(row), Integer.toString(col), Integer.toString(i)));
            termBuilder.add(new Predicate.Atom(neighbor.first.toString(), neighbor.second.toString(), Integer.toString(i)));
            builder.add(new Predicate.Neg(termBuilder.build()));
          }
        }
      }
      Romdd<Boolean> newConstraints = builder.build().toRomdd(dict);
      Romdd<Boolean> multiplied = Romdd.<Boolean>apply(Romdd.AND, newConstraints, stack.head());
      backStack = backStack.push(multiplied);
setMarked(row, col);      
System.out.println("vars after multiplication: " + getVarString(multiplied));
// TestRomdd.printTableOfReachableValues(multiplied);
// testPairWiseConstraints(multiplied);
      Romdd<Boolean> summed = multiplied.sum(Romdd.OR, row + "." + col);
System.out.println("vars after summation:      " + getVarString(summed));
// TestRomdd.printTableOfReachableValues(summed);
// testPairWiseConstraints(summed);  // this method is useless because only ONE node has its constraints fully instantiated
      stack = stack.push(summed);
    }
    
    public String getVarString(Romdd<Boolean> romdd) {
      StringBuilder builder = new StringBuilder("[");
      boolean isFirst = true;
      for (String varName : romdd.listVarNames()) {
        if (isFirst) isFirst = false;
        else builder.append(", ");
        builder.append(varName);
        Pair<Integer> pair = toPair(varName);
        if (isMarked(pair)) builder.append('*');
      }
      builder.append(']');
      return builder.toString();
    }
    
    public void testPairWiseConstraints(Romdd<Boolean> eq) {
      TreeSet<String> vars = eq.listVarNames();
      for (String v1 : vars) {
        for (String v2 : vars) {
          Pair<Integer> p1 = toPair(v1), p2 = toPair(v2);
          if (!(isMarked(p1) && isMarked(p2))) continue;  // only marked nodes can be expected to respect each other
          if (p1.compareTo(p2) >= 0 || !instance.isNeighbor(p1, p2)) continue;  // only need to check once per pair and not self
          
          for (int val = 1; val <= N; val++) {
            Romdd<Boolean> test = eq.restrict(p1.first + "." + p1.second, Integer.toString(val));
            test.restrict(p2.first + "." + p2.second, Integer.toString(val));
            test.accept(new Romdd.Visitor<Boolean>() {
              public void visitTerminal(Romdd.Terminal<Boolean> term) {
                if (term.output) {
                  throw new RuntimeException();
                }
              }
              public void visitNode(Romdd.Node<Boolean> node) {
                throw new RuntimeException();
              }
            });
          }
        }
      }
    }
    
    public Pair<Integer> findBestCandidate() {
      Pair<Integer> rv = null;
      int minUnmarkedNeighbors = Integer.MAX_VALUE;
      
      for (int row = 0; row < N; row++) {
        for (int col = 0; col < N; col++) {
          if (isMarked(row, col)) continue;  // marked nodes cannot be candidates

          int count = 0;
          for (Pair<Integer> neighbor : instance.getNeighbors(row, col)) {
            if (!isMarked(neighbor)) {
              count++;
            }
          }
          if (count < minUnmarkedNeighbors) {
            rv = new Pair<Integer>(row, col);
            minUnmarkedNeighbors = count;
          }
        }
      }
      return rv;
    }
    
    private static class ValueTable implements Comparable<ValueTable> {
      public final Pair<Integer> pair;
      public final TreeSet<String> values;
      
      public ValueTable(Pair<Integer> pair, TreeSet<String> values) {
        this.pair = pair;
        this.values = values;
      }
      
      public int compareTo(ValueTable t) {
        return values.size() - t.values.size();
      }
    }
    
    private Partition<ValueTable> getValueTable(Romdd<Boolean> tree) {
      Partition.Builder<ValueTable> builder = Partition.<ValueTable>naturalBuilder();
      for (TerminatedIterator<Pair<Integer>> it = unmarkedIterator(); it.hasNext(); ) {
        Pair<Integer> next = it.next();
        TreeSet<String> values = tree.findChildrenReaching(next.first + "." + next.second, true);
        System.out.println("Possible values for " + next + " = " + values);
        ValueTable table = new ValueTable(next, values);
        builder.add(table);
      }
      return builder.build();
    }

    private Entry findDetermined(Romdd<Boolean> tree) {
      for (int row = 0; row < N; row++) {
        for (int col = 0; col < N; col++) {
          if (isMarked(row,col)) continue;
          
          TreeSet<String> values = tree.findChildrenReaching(row + "." + col, true);
          if (values.size() == 1) {
            return new Entry(row, col, Integer.parseInt(values.iterator().next()));
          }
        }
      }
      return null;
    }
        
    private void setMarked(int i, int j) {
      setMarked(N*i + j);
    }
    
    private void setMarked(int idx) {
      if (!marked[idx]) {
        marked[idx] = true;
        markCount++;
      }
    }
      
    public boolean isMarked(int i, int j) {
      return marked[N*i + j];
    }
    
    public boolean isMarked(Pair<Integer> pair) {
      return isMarked(pair.first, pair.second);
    }
    
    void initializeMarkings() {  // initializes the markings
      TerminatedIterator<Entry> it = instance.nonZeroIterator();
      while (it.hasNext()) {
        Entry e = it.next();
        setMarked(e.row, e.col);
      }
    }
    
    /** We treat rows machines and cols as domains here. */
    private Dictionary buildDictionary() {
      Dictionary.Builder builder = new Dictionary.Builder();
      for (int row = 0; row < N; row++) {
        Domain.AltBuilder domBuilder = new Domain.AltBuilder(Integer.toString(row));
        for (int col = 0; col < N; col++) {
          Alphabet.AltBuilder alphaBuilder = new Alphabet.AltBuilder(Integer.toString(row), Integer.toString(col));
          for (int i = 1; i <= N; i++) {
            alphaBuilder.addCharacter(Integer.toString(i));
          }
          domBuilder.add(alphaBuilder.build());
        }
        builder.add(domBuilder.build());
      }
      return builder.build();
    }
  }
}
