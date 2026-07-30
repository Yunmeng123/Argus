package com.argus.vcs;

import com.argus.llm.TokenUsage;
import com.argus.model.*;
import com.argus.report.ReviewStore;
import com.argus.review.ReviewService;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;

class PrReviewServiceTest {
 private VcsProvider provider; private ReviewService reviewer; private ReviewStore store; private PrReviewService service; private PrTask task;
 @BeforeEach void init(){provider=mock(VcsProvider.class);when(provider.platform()).thenReturn("github");reviewer=mock(ReviewService.class);store=mock(ReviewStore.class);service=new PrReviewService(List.of(provider),reviewer,store);task=new PrTask("github","a/r",1,"sha","alice");}
 @Test void duplicateCommitSkipsExternalVcsAndLlmCalls(){when(store.existsVcsReview("GITHUB","a/r",1,"sha")).thenReturn(true);service.process(task);verify(provider,never()).fetchChanges(any());verifyNoInteractions(reviewer);}
 @Test void blankDiffSkipsLlmAndComments(){when(provider.fetchChanges(task)).thenReturn(new PrChanges(" ",null,null,null,"",null));service.process(task);verifyNoInteractions(reviewer);verify(provider,never()).postSummary(any(),any());}
 @Test void postsAnchoredFindingAndFallsBackToSummary(){when(provider.fetchChanges(task)).thenReturn(new PrChanges("diff",null,null,"sha","PR","url"));
  Finding anchored=new Finding("A.java",2,true,Severity.MAJOR,"bug","bad","detail","fix",.9), loose=new Finding("B.java",4,false,Severity.MINOR,"style","minor","","",.6);
  when(reviewer.review(anyString(),any())).thenReturn(result(List.of(anchored,loose)));when(provider.postLineComment(eq(task),any(),eq("A.java"),eq(2),any())).thenReturn(true);
  service.process(task);verify(provider).postLineComment(eq(task),any(),eq("A.java"),eq(2),contains("bad"));verify(provider).postSummary(eq(task),contains("B.java:4"));}
 private ReviewResult result(List<Finding> fs){return new ReviewResult("id", Instant.now(),"stub",1,1,List.of(),Map.of("MAJOR",1),fs,new TokenUsage(1,1,2),80,"ok",0,null);}
}
