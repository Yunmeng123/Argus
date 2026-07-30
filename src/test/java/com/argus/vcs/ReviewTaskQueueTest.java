package com.argus.vcs;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewTaskQueueTest {
 private PrReviewService service; private ManualExecutor executor; private ReviewTaskQueue queue;
 @BeforeEach void init(){service=mock(PrReviewService.class);executor=new ManualExecutor();queue=new ReviewTaskQueue(service,executor);}
 @AfterEach void close(){queue.shutdown();}
 @Test void highFrequencySamePrKeepsLatestAndEvictsOldTasks(){submit("a",1,"1");submit("a",1,"2");submit("a",1,"3");executor.runAll();verify(service).process(argThat(t->t.commitSha().equals("3")));verifyNoMoreInteractions(service);}
 @Test void duplicateCommitIsOnlyProcessedOnce(){submit("a",1,"1");submit("a",1,"1");executor.runAll();verify(service,times(1)).process(any());}
 @Test void differentPrTasksRunInParallel() throws Exception {
  ExecutorService workers=Executors.newFixedThreadPool(2);ReviewTaskQueue parallelQueue=new ReviewTaskQueue(service,workers);
  CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1);
  doAnswer(invocation->{entered.countDown();assertTrue(release.await(2,TimeUnit.SECONDS));return null;}).when(service).process(any());
  parallelQueue.submit(new PrTask("github","a",1,"1","me"));parallelQueue.submit(new PrTask("github","a",2,"2","me"));
  assertTrue(entered.await(2,TimeUnit.SECONDS),"both PRs should enter processing before either is released");release.countDown();workers.shutdown();assertTrue(workers.awaitTermination(2,TimeUnit.SECONDS));parallelQueue.shutdown();verify(service,times(2)).process(any());
 }
 @Test void processingExceptionDoesNotPreventFollowingTask(){doThrow(new RuntimeException("boom")).doNothing().when(service).process(any());submit("a",1,"1");submit("b",2,"2");assertDoesNotThrow(executor::runAll);verify(service,times(2)).process(any());}
 @Test void shutdownCancelsPendingWorkAndRejectsNewTasks(){submit("a",1,"1");queue.shutdown();assertTrue(executor.shutdown);assertThrows(RejectedExecutionException.class,()->submit("a",2,"2"));}
 private void submit(String repo,long pr,String sha){queue.submit(new PrTask("github",repo,pr,sha,"me"));}
 static class ManualExecutor extends AbstractExecutorService {final Deque<Runnable> tasks=new ArrayDeque<>();boolean shutdown;public void shutdown(){shutdown=true;}public List<Runnable> shutdownNow(){shutdown=true;var copy=List.copyOf(tasks);tasks.clear();return copy;}public boolean isShutdown(){return shutdown;}public boolean isTerminated(){return shutdown&&tasks.isEmpty();}public boolean awaitTermination(long t,TimeUnit u){return isTerminated();}public void execute(Runnable r){if(shutdown)throw new RejectedExecutionException();tasks.add(r);}void runAll(){while(!tasks.isEmpty())tasks.remove().run();}}
}
