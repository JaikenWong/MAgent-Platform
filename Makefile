# ───────── MAgent-Platform Makefile ─────────

.PHONY: help dev up down build backend frontend migrate seed lint typecheck \
        test test-be test-fe clean logs psql redis-cli

SHELL := /bin/bash
.DEFAULT_GOAL := help

ROOT := $(CURDIR)
BE    := $(ROOT)/backend
FE    := $(ROOT)/frontend

help: ## 显示所有命令
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-14s\033[0m %s\n",$$1,$$2}'

dev: up-detach logs ## 一键起本地开发栈 (docker compose up -d + logs)
up-detach:
	docker compose up -d postgres redis
up: ## 仅启动依赖 (postgres/redis)
	docker compose up -d postgres redis
up-full: ## 含 backend+frontend+traefik
	docker compose --profile full up -d --build

down: ## 停止全部
	docker compose --profile full down

build: build-be build-fe ## 编译前后端

build-be: ## 编译 backend (mvn package -DskipTests)
	cd $(BE) && mvn -q -B package -DskipTests

build-fe: ## 编译 frontend (npm run build)
	cd $(FE) && npm ci && npm run build

lint: lint-be lint-fe ## 跑前后端 lint

lint-be: ## backend: mvn checkstyle (编译即 lint)
	cd $(BE) && mvn -q -B compile

lint-fe: ## frontend: eslint + tsc
	cd $(FE) && npm run lint && npm run typecheck

typecheck: lint-fe ## 别名

test: test-be test-fe ## 跑全部测试
test-be: ## backend 测试
	cd $(BE) && mvn -q -B test
test-fe: ## frontend 测试
	cd $(FE) && npm run test -- --run

migrate: ## 执行 Flyway 迁移 (通过 backend)
	cd $(BE) && mvn -q -B spring-boot:run -Dspring-boot.run.arguments="--magent.migrate-only=true"
seed: ## 仅执行 seed
	cd $(BE) && mvn -q -B spring-boot:run -Dspring-boot.run.arguments="--magent.seed-only=true"

psql: ## 进 PostgreSQL
	@docker compose exec postgres psql -U $$(awk -F= '/POSTGRES_USER/{print $$2}' .env || echo magent) -d $$(awk -F= '/POSTGRES_DB/{print $$2}' .env || echo magent)

redis-cli: ## 进 Redis
	@docker compose exec redis redis-cli

logs: ## 跟随所有服务日志
	docker compose logs -f --tail=100 backend frontend

clean: ## 清理产物
	rm -rf $(BE)/target $(FE)/node_modules $(FE)/dist .data