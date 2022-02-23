package com.cloud.serverpak;


import messages.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MainHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LogManager.getLogger(ServerApp.class); // Trace < Debug < Info < Warn < Error < Fatal
    private AuthService authService;
    private static List<Channel> channels = new ArrayList<>();
    private String userName;

    public MainHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof AuthMessage) reqAuth(ctx, msg);              //пришел запрос на авторизацию пользователя

        if (msg instanceof RegUserRequest) regUserReq(ctx, msg);        // пришел запрос регистрации пользователя

        if (msg instanceof FileRequest) fileReq(ctx, msg);              // пришел запрос на скачивавние файла

        if (msg instanceof DellFileRequest) delFileReq(msg);           // пришел запрос на удаление файла

        if (msg instanceof FileMessage) fileMess(msg);                  // пришел файл
    }

    public void reqAuth (ChannelHandlerContext ctx, Object msg) throws IOException {
        String name = ((AuthMessage) msg).getLoginUser();
        String pass = ((AuthMessage) msg).getPassUser();
        userName = authService.getNickByLoginPass(name, pass);
        if (userName != null) {  // если сервис авторизации вернул имя
            channels.add(ctx.channel());
            AuthMessage authMessage = new AuthMessage(userName, listFiles(userName));
            ctx.writeAndFlush(authMessage);
            LOGGER.info("Авторизация пройдена успешно выслан список файлов на сервере");
        } else {
            ctx.writeAndFlush(new AuthMessage("none", ""));
            LOGGER.info("Авторизация НЕ пройдена.");
        }
    }

    public void  regUserReq(ChannelHandlerContext ctx, Object msg){
        RegUserRequest regMsg = (RegUserRequest) msg;
        // если запрошенный ник пользователя уже зарегистрирован в БД
        if (regMsg.getNameUser().equals(authService.getNickByLoginPass(regMsg.getLogin(), regMsg.getPassUser()))) {
            // то отправляем отказ в регистрации пользователя
            ctx.writeAndFlush(new RegUserRequest("none", "", ""));
        } else { // если ник свободен то регистрируем нового
            if (authService.registerNewUser(regMsg.getNameUser(), regMsg.getLogin(), regMsg.getPassUser())) {
                ctx.writeAndFlush(new RegUserRequest("reg", "", ""));
            }
        }
    }

    public void fileReq(ChannelHandlerContext ctx, Object msg){
        new Thread(() -> {
            try {
                String nameFile = ((FileRequest) msg).getFilename();
                File file = new File("server/files/" + userName + "/" + nameFile);
                int bufSize = 1024 * 1024 * 10;
                int partsCount = new Long(file.length() / bufSize).intValue();
                if (file.length() % bufSize != 0) {
                    partsCount++;
                }
                FileMessage fmOut = new FileMessage(nameFile, -1, partsCount, new byte[bufSize]);
                FileInputStream in = new FileInputStream(file);
                for (int i = 0; i < partsCount; i++) {
                    int readedBytes = in.read(fmOut.data);
                    fmOut.partNumber = i + 1;
                    if (readedBytes < bufSize) {
                        fmOut.data = Arrays.copyOfRange(fmOut.data, 0, readedBytes);
                    }
                    ctx.writeAndFlush(fmOut);
                    Thread.sleep(100);
                    System.out.println("Отправлена часть #" + (i + 1));
                }
                in.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void fileMess(Object msg) throws IOException {
        FileMessage fmsg = (FileMessage) msg;
        boolean append = true;
        if (fmsg.partsCount == 1) {
            append = false;
        }
        System.out.println(fmsg.partNumber + " / " + fmsg.partsCount);
        FileOutputStream fos = new FileOutputStream("server/files/" + userName + "/" + fmsg.filename, append);
        fos.write(fmsg.data);
        fos.close();
        if (fmsg.partNumber == fmsg.partsCount) {
            System.out.println("файл полностью получен");
        }
    }

    public void delFileReq(Object msg) throws IOException {
        String nameDelFile = ((DellFileRequest) msg).getNameFile();
        Path path = Paths.get("server/files/" + userName + "/" + nameDelFile);
        Files.delete(path);
        LOGGER.info("Пользователь " + userName + " удалил файл " + nameDelFile);
    }

    public String listFiles(String nameUser) throws IOException {
        if (nameUser != null) {
            Path path = Paths.get("server/files/" + nameUser);
            if (!Files.exists(path)) {
                Files.createDirectory(path);
            }
            // формируем лист файлов находящихся у сервера для клиента
            return Files.list(path).map((p) -> p.getFileName().toString()).collect(Collectors.joining(" "));
        }
        return "";
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
        LOGGER.info("Соединение закрыто");
    }
}