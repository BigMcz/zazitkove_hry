package cz.mibcon.zazitky;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZazitkyApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZazitkyApplication.class, args);
	}

}
