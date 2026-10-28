package cz.mibcon.zazitky;

import org.springframework.boot.SpringApplication;

public class TestZazitkyApplication {

	public static void main(String[] args) {
		SpringApplication.from(ZazitkyApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
