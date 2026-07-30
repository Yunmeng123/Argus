package com.argus.web;

import com.argus.config.ArgusProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import jakarta.servlet.FilterChain;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthFilterTest {
 @Test void protectsApiAndAcceptsAllSupportedTokenForms() throws Exception {
  ArgusProperties p=new ArgusProperties(); p.getSecurity().setAccessToken("token"); AuthFilter f=new AuthFilter(p);
  for (int mode=0;mode<3;mode++) { MockHttpServletRequest r=new MockHttpServletRequest("GET","/api/config");
   if(mode==0)r.addHeader("X-Argus-Token","token"); if(mode==1)r.addHeader("Authorization","Bearer token"); if(mode==2)r.setParameter("token","token");
   FilterChain c=mock(FilterChain.class); f.doFilter(r,new MockHttpServletResponse(),c); verify(c).doFilter(any(),any()); }
 }
 @Test void rejectsInvalidTokenButExemptsWebhooksAndPublicPaths() throws Exception {
  ArgusProperties p=new ArgusProperties(); p.getSecurity().setAccessToken("token"); AuthFilter f=new AuthFilter(p);
  MockHttpServletResponse out=new MockHttpServletResponse(); f.doFilter(new MockHttpServletRequest("GET","/api/config"),out,mock(FilterChain.class));
  assertEquals(401,out.getStatus()); assertTrue(out.getContentAsString().contains("未授权"));
  for(String path:new String[]{"/api/webhook/github","/assets/app.js"}) { FilterChain c=mock(FilterChain.class); f.doFilter(new MockHttpServletRequest("GET",path),new MockHttpServletResponse(),c); verify(c).doFilter(any(),any()); }
 }
}
