package com.learning.spring.controllers;

import java.security.Principal;
// import java.text.ParseException;
// import java.text.SimpleDateFormat;
// import java.util.ArrayList;
// import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.learning.spring.social.bindings.AddCommentForm;
import com.learning.spring.social.bindings.AddPostForm;
import com.learning.spring.social.bindings.RegistrationForm;
import com.learning.spring.social.dto.PostDTO;
import com.learning.spring.social.entities.Comment;
import com.learning.spring.social.entities.Favpost;
import com.learning.spring.social.entities.Like;
import com.learning.spring.social.entities.LikeId;
import com.learning.spring.social.entities.Mutedpost;
import com.learning.spring.social.entities.Notification;
import com.learning.spring.social.entities.Post;
import com.learning.spring.social.entities.Tag;
import com.learning.spring.social.entities.User;
import com.learning.spring.social.exceptions.ResourceNotFoundException;
import com.learning.spring.social.repositories.FavPostRepository;
import com.learning.spring.social.repositories.LikeCRUDRepository;
import com.learning.spring.social.repositories.MutedPostRepository;
import com.learning.spring.social.repositories.PostRepository;
import com.learning.spring.social.repositories.TagRepository;
import com.learning.spring.social.repositories.UserRepository;
import com.learning.spring.social.service.CommentService;
import com.learning.spring.social.service.DomainUserService;
import com.learning.spring.social.service.FavMutePostService;
import com.learning.spring.social.service.NotificationService;
import com.learning.spring.social.service.PostService;
// import com.learning.spring.social.service.SortingPosts;

import jakarta.servlet.ServletException;
// import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/forum")
public class ForumController {
    @Autowired
    private CommentService commentService;

    @Autowired
    private PostService postService;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private DomainUserService domainUserService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private LikeCRUDRepository likeCRUDRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MutedPostRepository mutedPostRepository;

    @Autowired
    private FavPostRepository favPostRepository;

    @Autowired
    private FavMutePostService favMutePostService;

    // @Autowired
    // private PostService postService;

    // @Autowired
    // private SortingPosts sortedService;

    @GetMapping
    public String home(Principal principal, Model model, @AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("isLoggedIn", principal != null);

        Optional<User> user = userRepository.findByName(userDetails.getUsername());
        List<Post> primposts = mutedPostRepository.findAllPostsNotMutedByUser(user.get());
        List<PostDTO> posts = postService.createPostDTO(primposts);

        if (principal != null) {
            model.addAttribute("username", principal.getName());
            model.addAttribute("symbol", principal.getName().substring(0, 1));
        } else {
            model.addAttribute("username", "anonymous");
            model.addAttribute("symbol", "a");
        }
        model.addAttribute("posts", postService.findAll());
        return "forum/home";
    }

    @GetMapping("/tag/{name}")
    public String getPostsByTag(@PathVariable String name, Model model, Principal principal) {
        model.addAttribute("isLoggedIn", principal != null);
        if (principal != null) {
            model.addAttribute("username", principal.getName());
            model.addAttribute("symbol", principal.getName().substring(0, 1));
        }
        model.addAttribute("posts", postService.findByPattern("#" + name));
        return "forum/home";
    }

    @GetMapping("/post/form")
    public String getPostForm(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("postForm", new AddPostForm());
        return "forum/postForm";
    }

    @GetMapping("/search")
    public String searchPost(@RequestParam("search") String search, Principal principal, Model model) {
        model.addAttribute("isLoggedIn", principal != null);
        if (principal != null) {
            model.addAttribute("username", principal.getName());
            model.addAttribute("symbol", principal.getName().substring(0, 1));
        }
        if (search == null || search.isEmpty()) {
            return "redirect:/forum";
        } else {
            model.addAttribute("posts", postService.findByPattern(search));
        }
        return "forum/home";
    }

    @PostMapping("/post/add")
    @Transactional
    public String addNewPost(@ModelAttribute("postForm") AddPostForm postForm, BindingResult bindingResult,
            RedirectAttributes attr, @AuthenticationPrincipal UserDetails userDetails) throws ServletException {
        if (bindingResult.hasErrors()) {
            System.out.println(bindingResult.getFieldErrors());
            attr.addFlashAttribute("org.springframework.validation.BindingResult.post", bindingResult);
            attr.addFlashAttribute("post", postForm);
            return "redirect:/forum/post/form";
        }
        Set<Tag> postTags = new HashSet<>();
        String[] tags = postForm.getTags().split(",");
        for (int i = 0; i < tags.length; i++) {
            Tag existingTag = tagRepository.findByName(tags[i]);
            if (existingTag == null) {
                Tag newTag = new Tag();
                newTag.setName(tags[i]);
                tagRepository.save(newTag);
                postTags.add(newTag);
            } else {
                postTags.add(existingTag);
            }
        }
        User user = domainUserService.getByName(userDetails.getUsername()).get();
        Post post = new Post();
        post.setAuthor(user);
        post.setContent(postForm.getContent());
        post.setTitle(postForm.getTitle());
        post.setTags(postTags);
        postRepository.save(post);
        notificationService.createNotification(user, post, "POST", "You added a post (" + postForm.getTitle() + ").");

        return "redirect:/forum";
    }

    @PostMapping("/post/{id}/like")
    public String postLike(@PathVariable int id, @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes attr) {
        User user = domainUserService.getByName(userDetails.getUsername()).get();
        // notificationService.createNotification(user,postRepository.findById(id).get(),
        // "like", user.getName()+" Liked a post!");
        LikeId likeId = new LikeId();
        Post post = postRepository.findById(id).get();
        likeId.setUser(domainUserService.getByName(userDetails.getUsername()).get());
        likeId.setPost(postRepository.findById(id).get());
        Like like = new Like();
        like.setLikeId(likeId);
        likeCRUDRepository.save(like);
        if (userRepository.findByName(userDetails.getUsername()).get().equals(post.getAuthor())) {
            notificationService.createNotification(postRepository.findById(id).get().getAuthor(),
                    postRepository.findById(id).get(), "LIKE",
                    "you, liked your post (" + postRepository.findById(id).get().getTitle() + ").");
        } else {
            notificationService.createNotification(postRepository.findById(id).get().getAuthor(),
                    postRepository.findById(id).get(), "LIKE", userDetails.getUsername() + " liked your post ("
                            + postRepository.findById(id).get().getTitle() + ").");
        }
        return "redirect:/forum";
    }

    @PostMapping("/post/{id}/comment")
    public String commentOnPost(@ModelAttribute("commentForm") AddCommentForm commentForm, @PathVariable int id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = domainUserService.getByName(userDetails.getUsername()).get();
        // notificationService.createNotification(user,postRepository.findById(id).get(),
        // "comment", user.getName()+" Commented on the post!");
        Post post = postRepository.findById(id).get();
        int postId = post.getId();
        Comment comment = new Comment();
        comment.setContent(commentForm.getContent());
        comment.setPost(post);
        comment.setUser(domainUserService.getByName(userDetails.getUsername()).get());
        commentService.save(comment);
        if (userRepository.findByName(userDetails.getUsername()).get().equals(post.getAuthor())) {
            notificationService.createNotification(postRepository.findById(postId).get().getAuthor(), post, "COMMENT",
                    "You, commented on your post (" + post.getTitle() + ").");
        } else {
            notificationService.createNotification(postRepository.findById(postId).get().getAuthor(), post, "COMMENT",
                    userDetails.getUsername() + ", commented on your post (" + post.getTitle() + ").");
        }
        return "redirect:/forum";
    }

    @PostMapping("/post/{id}/reply/{parentId}")
    public String replyToComment(@RequestParam("content") String content, @PathVariable int id,
            @PathVariable int parentId, @AuthenticationPrincipal UserDetails userDetails) {
        Optional<Post> post = postRepository.findById(id);
        Comment comment = new Comment();
        comment.setContent(content);
        comment.setPost(post.get());
        comment.setUser(domainUserService.getByName(userDetails.getUsername()).get());
        comment.setParent(commentService.findById(parentId).get());
        commentService.save(comment);
        return "redirect:/forum";
    }

    @GetMapping("/register")
    public String getRegistrationForm(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
        return "forum/register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("registrationForm") RegistrationForm registrationForm,
            BindingResult bindingResult,
            RedirectAttributes attr) {
        if (bindingResult.hasErrors()) {
            attr.addFlashAttribute("org.springframework.validation.BindingResult.registrationForm", bindingResult);
            attr.addFlashAttribute("registrationForm", registrationForm);
            return "redirect:/register";
        }
        if (!registrationForm.isValid()) {
            attr.addFlashAttribute("message", "Passwords must match");
            attr.addFlashAttribute("registrationForm", registrationForm);
            return "redirect:/register";
        }
        domainUserService.save(registrationForm.getUsername(), registrationForm.getPassword());
        attr.addFlashAttribute("result", "Registration success!");
        return "redirect:/login";
    }

    @GetMapping("/notifications")
    public String notificationPage(Model model, @AuthenticationPrincipal UserDetails userDetails, Principal principal)
            throws ResourceNotFoundException {
        // List<Notification> notificationList = notificationRepository.findAll();
        model.addAttribute("isLoggedIn", principal != null);
        if (principal != null) {
            model.addAttribute("username", principal.getName());
        }
        User user = userRepository.findByName(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        System.out.println("inside get of notification");
        List<Notification> notificationList = notificationService.getNotificationsForUser(user);
        System.out.println(notificationList.toString());
        model.addAttribute("notificationList", notificationList);
        return "forum/notification";
    }

    @PostMapping("/notification/{notificationId}")
    public String handleNotificationForm(@PathVariable("notificationId") int postId) {
        System.out.println("Received notification ID: " + postId);
        System.out.println("----------------------------------------");

        return String.format("redirect:/forum/post/%d", postId);
    }

    // adding post to fav
    @PostMapping("/post/{id}/fav")
    public String addFavoritePost(@PathVariable int id, @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        String result = favMutePostService.addFavoritePost(id, userDetails);

        if (result.equals("redirect:/forum")) {
            return "redirect:/forum";
        } else {
            redirectAttributes.addFlashAttribute("FavMessage", result);
            return "redirect:/forum";
        }
    }

    // show the favourite post feed
    @GetMapping("/post/favfeed")
    public String favpostfeed(Model model, @AuthenticationPrincipal UserDetails userDetails)
            throws ResourceNotFoundException {
        Optional<User> user = userRepository.findByName(userDetails.getUsername());
        // favpostList = favPostRepository.findAllByUser(user.get());
        favMutePostService.favpostList = favMutePostService.findAllFavPostsByUser(user.get());
        model.addAttribute("favpostList", favMutePostService.favpostList);
        model.addAttribute("commenterName", userDetails.getUsername());
        return "forum/favPost";
    }

    // delete a post from favourite feed
    @PostMapping("/post/favfeed/{postId}/delete")
    public String deleteFavPost(@PathVariable int postId, String commenterName) {
        Optional<User> user = userRepository.findByName(commenterName);
        Optional<Post> post = postRepository.findById(postId);
        if (user.isPresent() && post.isPresent()) {
            favMutePostService.deleteFavPost(user.get(), post.get());
            return "redirect:/forum/post/favfeed";
        }
        return "redirect:/forum/post/error";
    }

    // muting a post
    @PostMapping("/post/{id}/mute")
    public String mutePost(@PathVariable int id, @AuthenticationPrincipal UserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        String result = favMutePostService.mutePost(id, userDetails);

        if (result.equals("redirect:/forum")) {
            return "redirect:/forum";
        } else {
            redirectAttributes.addFlashAttribute("MuteMessage", result);
            return "redirect:/forum";
        }
    }

    // show mute feed
    @GetMapping("/post/mutefeed")
    public String mutedpostfeed(Model model, @AuthenticationPrincipal UserDetails userDetails)
            throws ResourceNotFoundException {
        Optional<User> user = userRepository.findByName(userDetails.getUsername());
        favMutePostService.mutedpostList = mutedPostRepository.findAllByUser(user.get());
        model.addAttribute("mutedpostList", favMutePostService.mutedpostList);
        model.addAttribute("commenterName", userDetails.getUsername());
        return "forum/mutePost";
    }

    // unmuting a post
    @PostMapping("/post/mutefeed/{postId}/delete")
    public String unmutePost(@PathVariable int postId, String commenterName) {
        return favMutePostService.unmutePost(postId, commenterName);
    }

}