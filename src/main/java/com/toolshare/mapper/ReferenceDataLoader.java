package com.toolshare.mapper;

import com.toolshare.entity.HelpPost;
import com.toolshare.entity.Tool;
import com.toolshare.entity.ToolBox;
import com.toolshare.entity.User;
import com.toolshare.repository.HelpPostRepository;
import com.toolshare.repository.ToolBoxRepository;
import com.toolshare.repository.ToolRepository;
import com.toolshare.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 关联参考数据的批量加载器：把 DTO 映射时需要的「名称/实体」按 ID 集合一次性查出，
 * 避免单条映射逐个 findById 造成的 N+1 查询。
 */
@Component
public class ReferenceDataLoader {

    private final UserRepository userRepository;
    private final ToolRepository toolRepository;
    private final ToolBoxRepository toolBoxRepository;
    private final HelpPostRepository helpPostRepository;

    public ReferenceDataLoader(UserRepository userRepository,
                               ToolRepository toolRepository,
                               ToolBoxRepository toolBoxRepository,
                               HelpPostRepository helpPostRepository) {
        this.userRepository = userRepository;
        this.toolRepository = toolRepository;
        this.toolBoxRepository = toolBoxRepository;
        this.helpPostRepository = helpPostRepository;
    }

    public Map<Long, User> getUsers(Collection<Long> userIds) {
        return load(userIds, userRepository::findAllById, User::getId);
    }

    public Map<Long, String> getUserNames(Collection<Long> userIds) {
        return mapValues(getUsers(userIds), User::getUsername);
    }

    public Map<Long, Tool> getTools(Collection<Long> toolIds) {
        return load(toolIds, toolRepository::findAllById, Tool::getId);
    }

    public Map<Long, String> getToolNames(Collection<Long> toolIds) {
        return mapValues(getTools(toolIds), Tool::getName);
    }

    public Map<Long, ToolBox> getToolBoxes(Collection<Long> toolBoxIds) {
        return load(toolBoxIds, toolBoxRepository::findAllById, ToolBox::getId);
    }

    public Map<Long, String> getToolBoxNames(Collection<Long> toolBoxIds) {
        return mapValues(getToolBoxes(toolBoxIds), ToolBox::getName);
    }

    public Map<Long, HelpPost> getHelpPosts(Collection<Long> helpPostIds) {
        return load(helpPostIds, helpPostRepository::findAllById, HelpPost::getId);
    }

    private static <T> Map<Long, T> load(Collection<Long> ids,
                                         Function<List<Long>, List<T>> finder,
                                         Function<T, Long> idGetter) {
        List<Long> distinctIds = normalizeIds(ids);
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return finder.apply(distinctIds).stream()
                .collect(Collectors.toMap(idGetter, Function.identity(), (a, b) -> a));
    }

    private static <T> Map<Long, String> mapValues(Map<Long, T> source, Function<T, String> valueGetter) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> valueGetter.apply(e.getValue()), (a, b) -> a));
    }

    private static List<Long> normalizeIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> distinct = new HashSet<>();
        for (Long id : ids) {
            if (id != null) {
                distinct.add(id);
            }
        }
        return List.copyOf(distinct);
    }
}
