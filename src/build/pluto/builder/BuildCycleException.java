package build.pluto.builder;

import java.util.Objects;

import build.pluto.BuildUnit;

public class BuildCycleException extends RuntimeException {

  /**
   * 
   */
  private static final long serialVersionUID = -8981404220171314788L;

  public static enum CycleState {
    RESOLVED, NOT_RESOLVED, UNHANDLED
  }


  /**
   * The {@link BuildUnit} that caused the cycle.
   */
  private final BuildRequest<?, ?, ?, ?> cycleCause;
  /**
   * The set 
   */
  private final BuildCycle cycle;
  private CycleState cycleState = CycleState.UNHANDLED;

  public BuildCycleException(String message, BuildRequest<?, ?, ?, ?> cycleCause, BuildCycle cycle) {
    super(message);
    Objects.requireNonNull(cycle);
    Objects.requireNonNull(cycleCause);
    assert cycle.getCycleComponents().contains(cycleCause) : 
      "Cause " + cycleCause.createBuilder().description() + " not in cycle {" + cycle.description() + "}";
    this.cycleCause = cycleCause;
    this.cycle = cycle;
  }

  public BuildCycle getCycle() {
    return cycle;
  }

  public boolean isFirstInvokedOn(BuildRequest<?, ?, ?, ?> unit) {
    return cycleCause.equals(unit);
  }

  public void setCycleState(CycleState cycleState) {
    this.cycleState = cycleState;
  }

  public CycleState getCycleState() {
    return cycleState;
  }

  public BuildRequest<?, ?, ?, ?> getCycleCause() {
    return cycleCause;
  }

}
