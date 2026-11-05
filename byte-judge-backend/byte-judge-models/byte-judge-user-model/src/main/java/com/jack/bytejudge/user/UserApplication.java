package com.jack.bytejudge.user;

import com.jack.bytejudge.user.account.interfaces.cli.CreateAdminCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserApplication {

    public static void main(String[] args) {
        if (args.length > 0 && "--create-admin".equals(args[0])) {
            if (args.length != 1) {
                System.err.println("ADMIN_INPUT_INVALID: provide credentials through console or UTF-8 stdin, not arguments.");
                System.exit(2);
                return;
            }
            System.exit(CreateAdminCommand.run());
            return;
        }
        SpringApplication.run(UserApplication.class, args);
    }
}
