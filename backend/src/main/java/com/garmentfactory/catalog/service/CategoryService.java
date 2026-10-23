package com.garmentfactory.catalog.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.catalog.dto.CatalogDtos.CategoryRequest;
import com.garmentfactory.catalog.dto.CatalogDtos.CategoryResponse;
import com.garmentfactory.catalog.entity.Category;
import com.garmentfactory.catalog.repository.CategoryRepository;
import com.garmentfactory.shared.exception.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The category tree, at most three levels deep. Categories are deactivated, never deleted. */
@Service
public class CategoryService {

    public static final int MAX_DEPTH = 3;

    private final CategoryRepository categories;
    private final AuditRecorder audit;

    public CategoryService(CategoryRepository categories, AuditRecorder audit) {
        this.categories = categories;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        List<Category> all = categories.findAllByOrderBySortOrderAscNameAsc();
        CategoryTree tree = treeOf(all);
        Map<Long, String> publicIds = all.stream().collect(Collectors.toMap(Category::getId, Category::getPublicId));
        return all.stream()
                .map(category -> CatalogMapper.toResponse(category,
                        category.getParentId() == null ? null : publicIds.get(category.getParentId()),
                        tree.depth(category.getId())))
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category parent = resolveParent(request.parentId());
        if (parent != null && treeOf(categories.findAll()).depth(parent.getId()) + 1 > MAX_DEPTH) {
            throw tooDeep();
        }

        Category category = new Category();
        category.setName(request.name().trim());
        category.setParentId(parent == null ? null : parent.getId());
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        category.setActive(request.active() == null || request.active());
        category.setSlug(Slugs.unique(request.name(), categories::existsBySlug));
        categories.save(category);

        audit.record(AuditEvent.of("CATEGORY_CREATED", "CATEGORY", category.getPublicId(), "name=" + category.getName()));
        return respond(category);
    }

    @Transactional
    public CategoryResponse update(String publicId, CategoryRequest request) {
        Category category = find(publicId);
        Category newParent = resolveParent(request.parentId());
        Long newParentId = newParent == null ? null : newParent.getId();

        if (!Objects.equals(newParentId, category.getParentId())) {
            if (newParent != null) {
                CategoryTree tree = treeOf(categories.findAll());
                if (tree.isSelfOrDescendant(newParent.getId(), category.getId())) {
                    throw ApiException.badRequest("CATEGORY_CYCLE",
                            "A category cannot be moved under itself or one of its own subcategories.");
                }
                if (tree.depth(newParent.getId()) + tree.height(category.getId()) > MAX_DEPTH) {
                    throw tooDeep();
                }
            }
            category.setParentId(newParentId);
        }
        category.setName(request.name().trim());
        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }

        audit.record(AuditEvent.of("CATEGORY_UPDATED", "CATEGORY", category.getPublicId(),
                "name=" + category.getName() + ", active=" + category.isActive()));
        return respond(category);
    }

    private CategoryResponse respond(Category category) {
        List<Category> all = categories.findAll();
        CategoryTree tree = treeOf(all);
        String parentPublicId = all.stream()
                .filter(other -> other.getId().equals(category.getParentId()))
                .map(Category::getPublicId)
                .findFirst()
                .orElse(null);
        return CatalogMapper.toResponse(category, parentPublicId, tree.depth(category.getId()));
    }

    private Category find(String publicId) {
        return categories.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Category not found."));
    }

    private Category resolveParent(String parentPublicId) {
        if (parentPublicId == null || parentPublicId.isBlank()) {
            return null;
        }
        return categories.findByPublicId(parentPublicId)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_PARENT_CATEGORY", "The parent category does not exist."));
    }

    private static CategoryTree treeOf(List<Category> all) {
        return new CategoryTree(all.stream().map(c -> new CategoryTree.Node(c.getId(), c.getParentId())).toList());
    }

    private static ApiException tooDeep() {
        return ApiException.badRequest("CATEGORY_TOO_DEEP", "Categories can be at most " + MAX_DEPTH + " levels deep.");
    }
}