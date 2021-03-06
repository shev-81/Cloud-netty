package handlers;

import com.cloud.serverpak.MainHandler;
import lombok.Data;
import messages.*;

import java.util.HashMap;
import java.util.Map;

@Data
public class HandlerRegistry {

    private Map<Class<? extends AbstractMessage>, RequestHandler> mapHandlers;

    public HandlerRegistry(MainHandler mainHandler) {
        this.mapHandlers = new HashMap<>();
        mapHandlers.put(AuthMessage.class, new AuthHandler(mainHandler)::authHandle);
        mapHandlers.put(RegUserRequest.class, new RegUserHandler(mainHandler)::regHandle);
        mapHandlers.put(FileRequest.class, new ReqFileHandler(mainHandler)::reqFileHandle);
        mapHandlers.put(DelFileRequest.class, new DelFileHandler(mainHandler)::delHandle);
        mapHandlers.put(FileMessage.class, new FileHandler(mainHandler)::fileHandle);
        mapHandlers.put(FilesSizeRequest.class, new FilesListRequestHandler(mainHandler)::filesListHandle);
    }

    public RequestHandler getHandler(Class cl) {
        return mapHandlers.get(cl);
    }
}
