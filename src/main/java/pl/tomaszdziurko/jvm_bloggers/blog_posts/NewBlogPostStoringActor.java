package pl.tomaszdziurko.jvm_bloggers.blog_posts;

import static com.google.common.base.MoreObjects.firstNonNull;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;

import com.sun.syndication.feed.synd.SyndEntry;

import lombok.experimental.ExtensionMethod;
import lombok.extern.slf4j.Slf4j;
import pl.tomaszdziurko.jvm_bloggers.blog_posts.domain.BlogPost;
import pl.tomaszdziurko.jvm_bloggers.blog_posts.domain.BlogPostRepository;
import pl.tomaszdziurko.jvm_bloggers.utils.DateTimeUtilities;

import java.util.Date;
import java.util.Optional;

@Slf4j
@ExtensionMethod(DateTimeUtilities.class)
public class NewBlogPostStoringActor extends AbstractActor {

    private final BlogPostRepository blogPostRepository;

    public NewBlogPostStoringActor(BlogPostRepository blogPostRepository) {
        this.blogPostRepository = blogPostRepository;

        receive(ReceiveBuilder.match(RssEntryWithAuthor.class, rssEntry -> {
                Optional<BlogPost> existingPost = blogPostRepository.findByUrl(rssEntry.getRssEntry().getLink());
                if (!existingPost.isPresent()) {
                    storeNewBlogPost(rssEntry);
                } else {
                    log.trace("Existing post found, skipping save");
                }
            }
        ).build());
    }

    private void storeNewBlogPost(RssEntryWithAuthor rssEntry) {
        SyndEntry postInRss = rssEntry.getRssEntry();
        Date dateToStore = firstNonNull(postInRss.getPublishedDate(), postInRss.getUpdatedDate());
        BlogPost newBlogPost = BlogPost.builder()
                .title(postInRss.getTitle())
                .url(postInRss.getLink())
                .publishedDate(dateToStore.convertDateToLocalDateTime())
                .approved(rssEntry.getBlog().getDefaultApprovedValue())
                .blog(rssEntry.getBlog())
                .build();
        blogPostRepository.save(newBlogPost);
        log.info("Stored new post '{}' with id {} by {}", newBlogPost.getTitle(), newBlogPost.getId(), rssEntry.getBlog().getAuthor());
    }

    public static Props props(BlogPostRepository blogPostRepository) {
        return Props.create(NewBlogPostStoringActor.class, () -> {
                return new NewBlogPostStoringActor(blogPostRepository);
            }
        );
    }
}
