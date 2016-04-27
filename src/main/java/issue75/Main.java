package issue75;

import java.io.File;
import java.net.URISyntaxException;
import java.time.Duration;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import reactor.core.publisher.Computations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Timer;

import org.springframework.core.io.buffer.DataBufferAllocator;
import org.springframework.core.io.buffer.DefaultDataBufferAllocator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

public class Main {


	public static void main(String[] args) throws LifecycleException, URISyntaxException {

		ServletHttpHandlerAdapter servlet = new ServletHttpHandlerAdapter();
		servlet.setHandler(new AsyncHandler());

		Tomcat server = new Tomcat();
		server.setPort(8080);
		File base = new File(System.getProperty("java.io.tmpdir"));
		Context rootContext = server.addContext("", base.getAbsolutePath());
		Tomcat.addServlet(rootContext, "httpHandlerServlet", servlet);
		rootContext.addServletMapping("/", "httpHandlerServlet");
		server.start();

		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> response = restTemplate.getForEntity("http://localhost:8080", String.class);

		Assert.isTrue(response.getStatusCode().equals(HttpStatus.OK));
		Assert.isTrue(response.getBody().equals("hello"));
	}


	private static class AsyncHandler implements HttpHandler {

		private final Scheduler asyncGroup = Computations.parallel();

		private final DataBufferAllocator allocator = new DefaultDataBufferAllocator();

		@Override
		public Mono<Void> handle(ServerHttpRequest request, ServerHttpResponse response) {
			return response.setBody(Flux.just("h", "e", "l", "l", "o")
					.useTimer(Timer.global())
					.delay(Duration.ofMillis(100))
					.publishOn(asyncGroup)
					.collect(allocator::allocateBuffer,
							(buffer, str) -> buffer.write(str.getBytes())));
		}
	}

}

