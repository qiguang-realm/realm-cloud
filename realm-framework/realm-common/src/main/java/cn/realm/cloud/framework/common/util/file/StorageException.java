package cn.realm.cloud.framework.common.util.file;

import java.io.Serial;

/**
 * 文件存储异常。
 *
 * <p>所有 {@link FileStorage} 实现的方法在失败时都应抛出本异常或其子类。
 *
 * <p><b>继承自 {@link RuntimeException}：</b>存储操作多为 IO 密集型，
 * 失败通常不可恢复，使用运行时异常避免污染方法签名。
 *
 * <p><b>异常类型判断：</b>
 * <ul>
 *     <li>{@link IllegalArgumentException}：参数非法（bucket / key 为空）</li>
 *     <li>{@link StorageException}：存储层失败（网络、权限、超时）</li>
 * </ul>
 *
 * @author QI Guang
 * @since 1.0.0
 */
public class StorageException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
