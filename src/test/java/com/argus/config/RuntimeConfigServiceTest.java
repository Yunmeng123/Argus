package com.argus.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeConfigServiceTest {
 private static final Path FILE=Path.of("data/argus-config.json"); private byte[] original;
 @BeforeEach void save() throws Exception {if(Files.exists(FILE))original=Files.readAllBytes(FILE);Files.deleteIfExists(FILE);}
 @AfterEach void restore() throws Exception {Files.deleteIfExists(FILE);if(original!=null){Files.createDirectories(FILE.getParent());Files.write(FILE,original);}}
 @Test void seedsUpdatesPersistsAndIncrementsVersion() throws Exception {ArgusProperties p=new ArgusProperties();p.getLlm().setModel("seed");RuntimeConfigService s=new RuntimeConfigService(p,new ObjectMapper());
  ConfigPatch patch=new ConfigPatch(null," secret "," next ",null,1.2,true,100,4,true,500,null,null,null,null,null,null,null,null);
  RuntimeConfig updated=s.update(patch);assertEquals("next",updated.getLlm().getModel());assertEquals("secret",updated.getLlm().getApiKey());assertEquals(2,s.version());assertTrue(Files.readString(FILE).contains("next"));}
 @Test void rejectsInvalidRangesWithoutChangingVersion(){RuntimeConfigService s=new RuntimeConfigService(new ArgusProperties(),new ObjectMapper());ConfigPatch bad=new ConfigPatch(null,null,null,null,2.1,null,null,null,null,null,null,null,null,null,null,null,null,null);assertThrows(IllegalArgumentException.class,()->s.update(bad));assertEquals(1,s.version());}
 @Test void invalidStoredJsonFallsBackToSeed() throws Exception {Files.createDirectories(FILE.getParent());Files.writeString(FILE,"not-json");ArgusProperties p=new ArgusProperties();p.getLlm().setModel("seed");assertEquals("seed",new RuntimeConfigService(p,new ObjectMapper()).current().getLlm().getModel());}
}
