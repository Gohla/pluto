package build.pluto.builder;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import build.pluto.BuildUnit;
import build.pluto.builder.factory.BuilderFactory;
import build.pluto.util.IReporting;

/**
 * The {@link FixpointCycleHandler} resolved cycles by fixpoint compiling: the
 * requests in the cycle are compiled again and again until a consistent state
 * is reached. The involved builders need to be designed to support this
 * strategy of resolving a cycle, otherwise no fixpoint may be detected and
 * compilation runs into an endless loop.
 * 
 * @author moritzlichter
 *
 */
public class FixpointCycleHandler extends CycleHandler {

  public static final CycleHandlerFactory of(final BuilderFactory<?, ?, ?>... builders) {
    return new CycleHandlerFactory() {
      
      @Override
      public CycleHandler createCycleSupport(BuildCycle cycle) {
        return new FixpointCycleHandler(cycle, builders);
      }
    };
  }

  /**
   * All BuilderFactories which are supported
   */
  private final List<BuilderFactory<?, ?, ?>> supportedBuilders;
  
  /**
   * Creates a new {@link FixpointCycleHandler} which is able to handle build
   * requests to builders created by the given factories. If the cycle contains
   * any builder which is not from the given factories, the support rejects to
   * compile the cycle.
   * 
   * @param supportedBuilders
   *          all supported builder factories
   */
  public FixpointCycleHandler(BuildCycle cycle, BuilderFactory<?, ?, ?>... supportedBuilders) {
    super(cycle);
    this.supportedBuilders = Arrays.asList(supportedBuilders);
  }

  @Override
  public String cycleDescription(BuildCycle cycle) {
    return "Fixpoint {" + cycle.description() + "}";
  }

  @Override
  public boolean canBuildCycle(BuildCycle cycle) {
    // Each builder in the cycle must be supported
    for (BuildRequest<?, ?, ?, ?> r : cycle.getCycleComponents())
      if (!supportedBuilders.contains(r.factory))
        return false;
    
    return true;
  }

  @Override
  public Set<BuildUnit<?>> buildCycle(BuildCycle cycle, BuildUnitProvider manager) throws Throwable {
    IReporting report = manager.report;
    FixpointCycleBuildResultProvider cycleManager = new FixpointCycleBuildResultProvider(manager, cycle);

    int numInterations = 1;
    boolean cycleConsistent = false;
    List<BuildRequest<?, ?, ?, ?>> cyclicRequests = cycle.getCycleComponents();

    // Compile the cycle again and again until the fixpoint is detected. The
    // fixpoint is reached if
    // the complete cycle is consistent, so during an iteration no builder
    // executed
    while (!cycleConsistent) {
      report.messageFromSystem("Fixpoint interation " + numInterations, false, 0);
      cycleManager.startNextIteration();
      try {
        // CycleComponents are in order if which they were required
        // Require the first one which is not consistent to their input
        for (BuildRequest<?, ?, ?, ?> req : cyclicRequests) {
          cycleManager.require(req, true);
        }
        cycleConsistent = !cycleManager.wasAnyBuilderExecutedInIteration();
      } catch (RequiredBuilderFailed e) {
        throw e;
      }
      numInterations++;
    }
    report.messageFromSystem("Fixpoint detected", false, 0);
    return cycleManager.getAllUnitsInCycle();
  }
}
