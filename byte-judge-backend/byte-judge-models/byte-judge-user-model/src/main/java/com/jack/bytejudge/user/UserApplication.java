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
            // The controlled initialization command only needs the database and password
            // components. Keep it independent of Nacos Config and the web runtime, so a missing
            // config center cannot block account provisioning.
            System.exit(CreateAdminCommand.run());
            return;
        }
        SpringApplication.run(UserApplication.class, args);
    }
}
