package com.code.server.db.model;

import com.code.server.constant.db.AgentChild;
import com.code.server.constant.db.AgentInfo;
import com.code.server.db.utils.BaseEntity;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.util.Date;

/**
 * Created by sunxianping on 2018/3/13.
 */

@DynamicUpdate
@Entity
@Table(name = "game_agent",
      indexes = {@Index(name = "id", columnList = "id"),
@Index(name="unionId",columnList = "unionId")})
public class GameAgent extends BaseEntity {

    @Id
    private long id;

    private String openId;

    private String unionId;

    private double rebate;

    private long partnerId;//合伙人id

    private long parentId;//上级

    private int isPartner;

    private String qrTicket;

    private String image;

    private String name;
    private String idCard;
    private Date createDate;

    private String phone;


    @Type(type = "json")
    @Lob
    @Column(columnDefinition = "json")
    private AgentInfo agentInfo  = new AgentInfo();

    @Type(type = "json")
    @Lob
    @Column(columnDefinition = "json")
    private AgentChild agentChild = new AgentChild();

    public long getId() {
        return id;
    }

    public GameAgent setId(long id) {
        this.id = id;
        return this;
    }

    public double getRebate() {
        return rebate;
    }

    public GameAgent setRebate(double rebate) {
        this.rebate = rebate;
        return this;
    }

    public long getParentId() {
        return parentId;
    }

    public GameAgent setParentId(long parentId) {
        this.parentId = parentId;
        return this;
    }

    public AgentChild getAgentChild() {
        return agentChild;
    }

    public GameAgent setAgentChild(AgentChild agentChild) {
        this.agentChild = agentChild;
        return this;
    }

    public long getPartnerId() {
        return partnerId;
    }

    public GameAgent setPartnerId(long partnerId) {
        this.partnerId = partnerId;
        return this;
    }

    public int getIsPartner() {
        return isPartner;
    }

    public GameAgent setIsPartner(int isPartner) {
        this.isPartner = isPartner;
        return this;
    }

    public String getQrTicket() {
        return qrTicket;
    }

    public GameAgent setQrTicket(String qrTicket) {
        this.qrTicket = qrTicket;
        return this;
    }

    public String getOpenId() {
        return openId;
    }

    public GameAgent setOpenId(String openId) {
        this.openId = openId;
        return this;
    }

    public String getUnionId() {
        return unionId;
    }

    public GameAgent setUnionId(String unionId) {
        this.unionId = unionId;
        return this;
    }

    public String getName() {
        return name;
    }

    public GameAgent setName(String name) {
        this.name = name;
        return this;
    }

    public String getIdCard() {
        return idCard;
    }

    public GameAgent setIdCard(String idCard) {
        this.idCard = idCard;
        return this;
    }

    public Date getCreateDate() {
        return createDate;
    }

    public GameAgent setCreateDate(Date createDate) {
        this.createDate = createDate;
        return this;
    }

    public String getPhone() {
        return phone;
    }

    public GameAgent setPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public AgentInfo getAgentInfo() {
        return agentInfo;
    }

    public GameAgent setAgentInfo(AgentInfo agentInfo) {
        this.agentInfo = agentInfo;
        return this;
    }

    public String getImage() {
        return image;
    }

    public GameAgent setImage(String image) {
        this.image = image;
        return this;
    }
}
