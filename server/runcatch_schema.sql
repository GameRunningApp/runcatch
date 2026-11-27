-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: runcatch
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `character`
--

DROP TABLE IF EXISTS `character`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `character` (
  `character_id` int NOT NULL,
  `name` varchar(50) NOT NULL,
  `emoji` varchar(20) NOT NULL,
  `category` varchar(20) NOT NULL,
  `condition` varchar(100) NOT NULL,
  `hint` varchar(200) NOT NULL,
  `description` varchar(200) NOT NULL,
  `required_distance` int DEFAULT NULL,
  `image_url` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`character_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `community_group`
--

DROP TABLE IF EXISTS `community_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_group` (
  `group_id` bigint NOT NULL AUTO_INCREMENT,
  `history_id` bigint NOT NULL,
  `group_month` date NOT NULL,
  `group_number` int NOT NULL,
  `member_count` int NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`group_id`),
  UNIQUE KEY `uq_group_month_number` (`group_month`,`group_number`),
  UNIQUE KEY `uq_group_id_month` (`group_id`,`group_month`),
  KEY `fk_group_history` (`history_id`),
  CONSTRAINT `fk_group_history` FOREIGN KEY (`history_id`) REFERENCES `community_grouping_history` (`history_id`),
  CONSTRAINT `chk_group_number` CHECK ((`group_number` > 0)),
  CONSTRAINT `chk_member_count` CHECK ((`member_count` between 1 and 20))
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `community_group_member`
--

DROP TABLE IF EXISTS `community_group_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_group_member` (
  `group_id` bigint NOT NULL,
  `user_id` varchar(100) NOT NULL,
  `group_month` date NOT NULL,
  `assigned_at` datetime NOT NULL,
  PRIMARY KEY (`group_id`,`user_id`),
  UNIQUE KEY `uq_user_group_month` (`user_id`,`group_month`),
  KEY `fk_member_group_month` (`group_id`,`group_month`),
  CONSTRAINT `fk_member_group_month` FOREIGN KEY (`group_id`, `group_month`) REFERENCES `community_group` (`group_id`, `group_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `community_grouping_history`
--

DROP TABLE IF EXISTS `community_grouping_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `community_grouping_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `group_month` date NOT NULL,
  `status` varchar(20) NOT NULL,
  `started_at` datetime NOT NULL,
  `finished_at` datetime DEFAULT NULL,
  `target_count` int NOT NULL,
  `excluded_count` int NOT NULL,
  `group_count` int NOT NULL,
  `execution_type` varchar(20) NOT NULL,
  `error_message` text,
  PRIMARY KEY (`history_id`),
  CONSTRAINT `chk_execution_type` CHECK ((`execution_type` in (_euckr'AUTO',_euckr'ADMIN'))),
  CONSTRAINT `chk_history_counts` CHECK (((`target_count` >= 0) and (`excluded_count` >= 0) and (`group_count` >= 0))),
  CONSTRAINT `chk_history_status` CHECK ((`status` in (_euckr'RUNNING',_euckr'SUCCESS',_euckr'FAILED')))
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `running_record`
--

DROP TABLE IF EXISTS `running_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `running_record` (
  `running_number` int NOT NULL AUTO_INCREMENT,
  `user_id` varchar(100) NOT NULL,
  `run_date` bigint NOT NULL,
  `environment` varchar(20) DEFAULT NULL,
  `distance` double DEFAULT NULL,
  `duration` double DEFAULT NULL,
  `pace` double DEFAULT NULL,
  `avg_speed` double DEFAULT NULL,
  `max_speed` double DEFAULT NULL,
  PRIMARY KEY (`running_number`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schema_version`
--

DROP TABLE IF EXISTS `schema_version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_version` (
  `version` int NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_character`
--

DROP TABLE IF EXISTS `user_character`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_character` (
  `user_id` varchar(100) NOT NULL,
  `character_id` int NOT NULL,
  `level` int NOT NULL DEFAULT '1',
  `exp` int NOT NULL DEFAULT '0',
  `is_main` tinyint(1) NOT NULL DEFAULT '0',
  `acquired_at` datetime NOT NULL,
  PRIMARY KEY (`user_id`,`character_id`),
  KEY `character_id` (`character_id`),
  CONSTRAINT `user_character_ibfk_1` FOREIGN KEY (`character_id`) REFERENCES `character` (`character_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping events for database 'runcatch'
--

--
-- Dumping routines for database 'runcatch'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-21 19:12:55
