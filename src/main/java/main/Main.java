package main;

public class Main {

    public static void main(String[] args) {
        String role = args[0];
        switch (role) {
            case "master": {
                MainMaster.main(new String[]{});
                break;
            }
            case "iot": {
                MainIoT.main(new String[]{args[1]});
                break;
            }
            case "worker": {
                MainWorkerSystem.main(new String[]{});
                break;
            }
        }
    }
}
