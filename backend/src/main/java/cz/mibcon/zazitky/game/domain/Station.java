package cz.mibcon.zazitky.game.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "station")
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(nullable = false)
    private int orderNo;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String storyText;

    @Column(nullable = false, columnDefinition = "text")
    private String taskText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskType taskType;

    @Column(nullable = false, columnDefinition = "text")
    private String rewardText;

    /** Spoken story / reward (MP3, path on the web); null = text only. */
    @Column(length = 500)
    private String storyAudioUrl;

    @Column(length = 500)
    private String rewardAudioUrl;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 300)
    private String imageAlt;

    /** The story is a listening task: its text is shown only on request (transcript). */
    @Column(nullable = false)
    private boolean listenOnly;

    /** Narrator's direction where to go after the station is solved; null = none. */
    @Column(columnDefinition = "text")
    private String nextText;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderNo")
    private List<Hint> hints = new ArrayList<>();

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AcceptedAnswer> acceptedAnswers = new ArrayList<>();

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderNo")
    private List<StationChoice> choices = new ArrayList<>();

    public void addHint(Hint hint) {
        hint.setStation(this);
        hints.add(hint);
    }

    public void addAcceptedAnswer(AcceptedAnswer answer) {
        answer.setStation(this);
        acceptedAnswers.add(answer);
    }

    public void addChoice(StationChoice choice) {
        choice.setStation(this);
        choices.add(choice);
    }

    public UUID getId() {
        return id;
    }

    public Game getGame() {
        return game;
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public int getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(int orderNo) {
        this.orderNo = orderNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStoryText() {
        return storyText;
    }

    public void setStoryText(String storyText) {
        this.storyText = storyText;
    }

    public String getTaskText() {
        return taskText;
    }

    public void setTaskText(String taskText) {
        this.taskText = taskText;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public void setTaskType(TaskType taskType) {
        this.taskType = taskType;
    }

    public String getRewardText() {
        return rewardText;
    }

    public void setRewardText(String rewardText) {
        this.rewardText = rewardText;
    }

    public String getStoryAudioUrl() {
        return storyAudioUrl;
    }

    public void setStoryAudioUrl(String storyAudioUrl) {
        this.storyAudioUrl = storyAudioUrl;
    }

    public String getRewardAudioUrl() {
        return rewardAudioUrl;
    }

    public void setRewardAudioUrl(String rewardAudioUrl) {
        this.rewardAudioUrl = rewardAudioUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageAlt() {
        return imageAlt;
    }

    public void setImageAlt(String imageAlt) {
        this.imageAlt = imageAlt;
    }

    public boolean isListenOnly() {
        return listenOnly;
    }

    public void setListenOnly(boolean listenOnly) {
        this.listenOnly = listenOnly;
    }

    public String getNextText() {
        return nextText;
    }

    public void setNextText(String nextText) {
        this.nextText = nextText;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public List<Hint> getHints() {
        return hints;
    }

    public List<AcceptedAnswer> getAcceptedAnswers() {
        return acceptedAnswers;
    }

    public List<StationChoice> getChoices() {
        return choices;
    }
}
