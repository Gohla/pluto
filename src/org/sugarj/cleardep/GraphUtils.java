package org.sugarj.cleardep;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import org.sugarj.common.util.Pair;

public class GraphUtils {

  /**
   * Calculates the strongly connected components (SCC) for the graph of all
   * compilations units reachable from the given root units. The SCCs (sets of
   * compilation units) are returned in inverse topological orders of the
   * directed acyclic graph of the SCCs. That means that, iff a SCC s1 is before
   * an SCC s2 in the result list, no compilation unit in s1 depends on any
   * compilation unit in s2.
   * 
   * @param rootUnits
   *          the compilations units to start searching with
   * @return a list of the SCCs in inverse topological order
   */
  public static List<Set<CompilationUnit>> calculateStronglyConnectedComponents(Iterable<? extends CompilationUnit> rootUnits) {
    return new TarjanAlgorithm().calculateStronglyConnectedUnits(rootUnits);
  }

  private static class TarjanAlgorithm {

    private int index;
    // Maps for assigning index and low links values to the vertices
    // (compilation units)
    private Map<CompilationUnit, Integer> unitIndices;
    private Map<CompilationUnit, Integer> unitLowLinks;

    // Result list
    private List<Set<CompilationUnit>> stronglyConnectedComponents;

    // The stack: keep units in deque for order an in a set for tests whether
    // the stack contains a unit
    // this is necessary to have O(1) tests
    private Deque<CompilationUnit> stack;
    private Set<CompilationUnit> stackUnits;

    private void stackPush(CompilationUnit unit) {
      this.stack.push(unit);
      this.stackUnits.add(unit);
    }

    private CompilationUnit stackPop() {
      CompilationUnit u = this.stack.pop();
      // It is safe just to remove u from the set because the algorithm only
      // pushes each node once on the stack
      // so there are no duplicates
      this.stackUnits.remove(u);
      return u;
    }

    private boolean stackContains(CompilationUnit u) {
      return this.stackUnits.contains(u);
    }

    // For pseudo code see e.g. here
    // https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm

    public List<Set<CompilationUnit>> calculateStronglyConnectedUnits(Iterable<? extends CompilationUnit> units) {
      this.index = 0;
      this.stack = new LinkedList<>();
      this.stackUnits = new HashSet<CompilationUnit>();
      this.unitIndices = new HashMap<CompilationUnit, Integer>();
      this.unitLowLinks = new HashMap<CompilationUnit, Integer>();
      this.stronglyConnectedComponents = new LinkedList<>();

      for (CompilationUnit unit : units) {
        if (!this.unitIndices.containsKey(unit)) {
          this.findStrongConnectedComponent(unit);
        }
      }
      return this.stronglyConnectedComponents;
    }

    private void findStrongConnectedComponent(CompilationUnit unit) {
      this.unitIndices.put(unit, this.index);
      this.unitLowLinks.put(unit, this.index);
      this.index++;

      this.stackPush(unit);

      for (CompilationUnit dep : unit.getModuleDependencies()) {
        if (this.unitIndices.containsKey(dep)) {
          if (this.stackContains(dep)) {
            this.unitLowLinks.put(unit, Math.min(this.unitLowLinks.get(unit), this.unitIndices.get(dep)));
          }
        } else {
          this.findStrongConnectedComponent(dep);
          this.unitLowLinks.put(unit, Math.min(this.unitLowLinks.get(unit), this.unitLowLinks.get(dep)));
        }
      }

      if (this.unitLowLinks.get(unit) == this.unitIndices.get(unit)) {
        Set<CompilationUnit> component = new HashSet<>();
        CompilationUnit u;
        do {
          u = this.stackPop();
          component.add(u);
        } while (u != unit);
        this.stronglyConnectedComponents.add(component);
      }

    }
  }

  /**
   * Sorts all units reachable from root on the spanning DAG in topological
   * order with respect to the DAG.
   * 
   * @param root
   *          the root unit to start at
   * @return the sorted units in topological order, that means, a unit u1 before
   *         a unit u2 in the list does not depend on u2 in the spanning DAG
   */
  public static List<CompilationUnit> sortTopologicalFrom(CompilationUnit root) {
    Objects.requireNonNull(root);
    LinkedList<CompilationUnit> sorting = new LinkedList<>();

    Map<CompilationUnit, Boolean> visitedUnits = new HashMap<>();
    Deque<Pair<CompilationUnit, Integer>> stack = new ArrayDeque<>();
    stack.push(Pair.create(root, 1));

    final Boolean NEW = null;
    final Boolean PENDING = Boolean.FALSE;
    final Boolean SORTED = Boolean.TRUE;
    while (!stack.isEmpty()) {
      Pair<CompilationUnit, Integer> p = stack.peek();
      Boolean status = visitedUnits.get(p.a);
      if (status == NEW) {
        // First visit of p.a
        visitedUnits.put(p.a, PENDING);
        boolean depAdded = false;
        for (CompilationUnit dep : p.a.getModuleDependencies()) {
          Boolean depstatus = visitedUnits.get(dep);
          if (depstatus == NEW) {
            stack.push(Pair.create(dep, 1));
            depAdded = true;
          }
          else if (depstatus == SORTED) {
            // already sorted due to another dependency
          }
          else if (depstatus == PENDING) {
            // cycle
            throw new RuntimeException("Cycle detected in topological sort");
          }
        }
        // Shorten: If no dep was pushed on the stack, we can finish p.a right
        // now and do not need
        // to push it on the stack to pop it in the next iteration again
        if (!depAdded) {
          sorting.add(p.a);
          visitedUnits.put(p.a, SORTED);
          stack.pop();
        }
      }
      else if (status == PENDING) {
        // was waiting on stack until all its deps got sorted
        sorting.add(p.a);
        visitedUnits.put(p.a, SORTED);
        stack.pop();
      }
      else if (status == SORTED) {
        // already sorted due to another dependency on it
        stack.pop();
      }
    }

    assert visitedUnits.size() == sorting.size();
    assert sorting.containsAll(visitedUnits.keySet());
    assert validateTopolocialSorting(sorting) : "Topolocial sorting is not valid " + visitedUnits;
    return sorting;

  }

  private static boolean validateTopolocialSorting(List<CompilationUnit> units) {
    for (int i = 0; i < units.size() - 1; i++) {
      for (int j = i + 1; j < units.size(); j++) {
        if (units.get(i).dependsOnTransitively(units.get(j))) {
          return false;
        }
      }
    }
    return true;
  }


}
