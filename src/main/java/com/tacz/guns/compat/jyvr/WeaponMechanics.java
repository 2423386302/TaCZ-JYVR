package com.tacz.guns.compat.jyvr;

/** Shared, deterministic reload topology. Default pack scripts override their misleading magazine feed flag. */
public record WeaponMechanics(Feed feed, Motion motion, String actionBone, String portBone, String gateBone) {
    public enum Feed { MAGAZINE, TUBE, CHAMBERS, SINGLE, BELT }
    public enum Motion { SLIDE, CHARGE, BOLT, PUMP, BREAK, CYLINDER, TRAP, NONE }
    public boolean loose(){return feed==Feed.TUBE || feed==Feed.CHAMBERS || feed==Feed.SINGLE;}
    public boolean gated(){return feed==Feed.CHAMBERS || feed==Feed.BELT || (feed==Feed.SINGLE && motion!=Motion.NONE);}
    public boolean retainsCases(){return motion==Motion.BOLT || motion==Motion.PUMP || feed==Feed.CHAMBERS || (feed==Feed.SINGLE && motion!=Motion.NONE);}
    public boolean returnStroke(){return motion==Motion.PUMP || motion==Motion.BOLT;}
    public float travel(){return motion==Motion.PUMP?.075f:motion==Motion.BOLT?.055f:.035f;}
    public static WeaponMechanics of(String id,String bolt,String feed){
        if(id.startsWith("tacz:"))switch(id.substring(5)){
            case "m870":return new WeaponMechanics(Feed.TUBE,Motion.PUMP,"slide2","magazine","");
            case "spas_12":return new WeaponMechanics(Feed.TUBE,Motion.PUMP,"pump","bullet_in_mag","");
            case "m1014":return new WeaponMechanics(Feed.TUBE,Motion.CHARGE,"Bolt","magazine","");
            case "kar98":return new WeaponMechanics(Feed.TUBE,Motion.BOLT,"bolt","bullet_in_gunbody","");
            case "db_long","db_short":return new WeaponMechanics(Feed.CHAMBERS,Motion.BREAK,"front_part","barrels","front_part");
            case "taurus500":return new WeaponMechanics(Feed.CHAMBERS,Motion.CYLINDER,"cranecylinder","cylinder","cranecylinder");
            case "taurus943":return new WeaponMechanics(Feed.CHAMBERS,Motion.CYLINDER,"cylinder_group","cylinder","cylinder_group");
            case "rhino357":return new WeaponMechanics(Feed.CHAMBERS,Motion.CYLINDER,"cylinder_crane","cylinder_crane","cylinder_crane");
            case "springfield1873":return new WeaponMechanics(Feed.SINGLE,Motion.TRAP,"trapdoor","chamber","trapdoor");
            case "m320":return new WeaponMechanics(Feed.SINGLE,Motion.BREAK,"chamber","chamber","chamber");
            case "lonetrail":return new WeaponMechanics(Feed.SINGLE,Motion.TRAP,"breach2","chamber","breach2");
            case "rpg7":return new WeaponMechanics(Feed.SINGLE,Motion.NONE,"","rocket","");
            case "fn_evolys":return new WeaponMechanics(Feed.BELT,Motion.CHARGE,"charge","magazine","cap");
            case "m249":return new WeaponMechanics(Feed.BELT,Motion.CHARGE,"pull_handle","magazine","cap");
            case "ai_awp":return new WeaponMechanics(Feed.MAGAZINE,Motion.BOLT,"bolt_group","magazine","");
            case "m700":return new WeaponMechanics(Feed.MAGAZINE,Motion.BOLT,"bullet_and_bolt","magazine","");
            case "m107":return new WeaponMechanics(Feed.MAGAZINE,Motion.CHARGE,"bolt","magazine","");
            case "m95":return new WeaponMechanics(Feed.MAGAZINE,Motion.BOLT,"m95_bolt","magazine","");
            case "b93r","m9a4":return new WeaponMechanics(Feed.MAGAZINE,Motion.SLIDE,"upper","magazine","");
            case "deagle","deagle_golden":return new WeaponMechanics(Feed.MAGAZINE,Motion.SLIDE,"slide2","magazine","");
            case "m4a1":return new WeaponMechanics(Feed.MAGAZINE,Motion.CHARGE,"m4a1_pull","magazine","");
            case "aug":return new WeaponMechanics(Feed.MAGAZINE,Motion.CHARGE,"aug_charge","magazine","");
            case "hk_g3":return new WeaponMechanics(Feed.MAGAZINE,Motion.CHARGE,"pull","magazine","");
            case "qbz_95":return new WeaponMechanics(Feed.MAGAZINE,Motion.CHARGE,"bolt_group","magazine","");
            case "glock_17","cz75","hk_mk23","m1911","p320","timeless50":
                return new WeaponMechanics(Feed.MAGAZINE,Motion.SLIDE,"slide","magazine","");
            case "p90":return new WeaponMechanics(Feed.MAGAZINE,Motion.CHARGE,"pull","magazine","");
        }
        return new WeaponMechanics("MANUAL".equals(feed)?Feed.TUBE:Feed.MAGAZINE,
            "MANUAL_ACTION".equals(bolt)?Motion.BOLT:Motion.CHARGE,"","magazine","");
    }
    public static boolean mayLoad(WeaponMechanics m,boolean open,int rounds,int capacity){return rounds>=0&&rounds<capacity&&(!m.gated()||open);}
    public static boolean completed(Motion motion,boolean rearReached,float distance,float side,float travel){
        if(!Float.isFinite(distance)||!Float.isFinite(side)||side>.07f)return false;
        return motion==Motion.PUMP||motion==Motion.BOLT?rearReached&&Math.abs(distance)<.018f:rearReached;
    }
}
