package capstone25_2.aim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class  AimApplication {

	public static void main(String[] args) {
		SpringApplication.run(AimApplication.class, args);
	}

}
