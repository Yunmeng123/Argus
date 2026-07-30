package com.argus.eval;

import com.argus.llm.TokenUsage;
import com.argus.model.*;
import com.argus.review.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvalServiceTest {
 private static final Path DIR=Path.of("samples/eval"), CASES=DIR.resolve("cases.json"), DIFF=DIR.resolve("unit-test.diff");private byte[] oldCases;
 @BeforeEach void setup() throws Exception {Files.createDirectories(DIR);if(Files.exists(CASES))oldCases=Files.readAllBytes(CASES);Files.writeString(DIFF,"diff");}
 @AfterEach void cleanup() throws Exception {Files.deleteIfExists(DIFF);if(oldCases==null)Files.deleteIfExists(CASES);else Files.write(CASES,oldCases);}
 @Test void calculatesRecallPrecisionToleranceAndTokens() throws Exception {Files.writeString(CASES,"[{\"name\":\"case\",\"diffFile\":\"unit-test.diff\",\"expected\":[{\"file\":\"A.java\",\"line\":10,\"category\":\"BUG\"}]}]");ReviewService reviewer=mock(ReviewService.class);
  List<Finding> findings=List.of(new Finding("A.java",12,true,Severity.MAJOR,"bug","hit","","",1),new Finding("B.java",1,true,Severity.INFO,"style","extra","","",1));
  when(reviewer.review(anyString(),any())).thenReturn(new ReviewResult("id",Instant.now(),"stub",1,1,List.of(),Map.of(),findings,new TokenUsage(3,4,7),null,null,0,null));
  var report=new EvalService(reviewer,new ObjectMapper()).run();assertEquals(1.0,report.recall());assertEquals(.5,report.precision());assertEquals(7,report.totalTokens());}
 @Test void recordsCaseFailureWithoutAbortingSuite() throws Exception {Files.writeString(CASES,"[{\"name\":\"broken\",\"diffFile\":\"missing.diff\",\"expected\":[{\"file\":\"A\",\"line\":1}]}]");var report=new EvalService(mock(ReviewService.class),new ObjectMapper()).run();assertEquals(0,report.totalHits());assertTrue(report.cases().get(0).missed().get(0).contains("用例执行失败"));}
 @Test void malformedManifestFailsClearly() throws Exception {Files.writeString(CASES,"{");assertThrows(IllegalStateException.class,()->new EvalService(mock(ReviewService.class),new ObjectMapper()).run());}
}
