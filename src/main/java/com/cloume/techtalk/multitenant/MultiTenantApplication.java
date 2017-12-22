package com.cloume.techtalk.multitenant;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoDbUtils;
import org.springframework.data.mongodb.core.MongoExceptionTranslator;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@Configuration
@ServletComponentScan
public class MultiTenantApplication {

	public static void main(String[] args) {
		SpringApplication.run(MultiTenantApplication.class, args);
	}

	@Controller
	@RequestMapping("/")
	static class WebController {
		@RequestMapping(method = {RequestMethod.GET})
		@ResponseBody
		String index(Principal user) {
			String userName = (user != null) ? user.getName() : "no-body";
			accessLogRepository.save(new AccessLog(userName));
			return "hello," + userName;
		}

		@Autowired
		AccessLogRepository accessLogRepository;
	}

	@Configuration
	@EnableWebSecurity
	static class WebSecurityConfig extends WebSecurityConfigurerAdapter {
		@Override
		protected void configure(AuthenticationManagerBuilder auth) throws Exception {
			auth
					.inMemoryAuthentication()
					.withUser("user").password("123").roles("USER")
					.and()
					.withUser("admin").password("123").roles("ADMIN");
		}
	}

	@Component
	@WebFilter(urlPatterns = "/*", filterName = "indexFilter2")
	public class IndexFilter2 implements Filter {
		@Override
		public void destroy() {
			System.out.println("filter2 destroy method");
		}

		@Override
		public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
				throws IOException, ServletException {
			HttpServletRequest r = (HttpServletRequest) req;
			if (r.getUserPrincipal() != null) {
				TenantContext.setCurrentTenant(r.getUserPrincipal().getName());
			}
			//chain.doFilter(req, resp);
		}

		@Override
		public void init(FilterConfig arg0) throws ServletException {
			System.out.println("filter2 init method");
		}
	}

	@Document(collection = "logs")
	static class AccessLog {
		@Id
		public String id;
		public String user;
		public Timestamp time = new Timestamp(System.currentTimeMillis());

		public AccessLog(String userName) {
			this.user = userName;
		}
	}

	class TenantData {
		final String tenant;
		final String dbName;
		final MongoClient client;

		public TenantData(String tenant, String dbName, MongoClient client) {
			this.tenant = tenant;
			this.dbName = dbName;
			this.client = client;
		}
	}

	static public class TenantContext {
		private static ThreadLocal<String> currentTenant = new ThreadLocal<>();

		public static void setCurrentTenant(String tenant) {
			currentTenant.set(tenant);
		}

		public static String getCurrentTenant() {
			return currentTenant.get();
		}
	}

	class MySimpleMongoDbFactory implements MongoDbFactory {
		private Map<String, TenantData> tenantDataMap = new HashMap<>();

		public MySimpleMongoDbFactory() {
			tenantDataMap.put("user",
					new TenantData("user", "tenant_user",
							new MongoClient()));
			tenantDataMap.put("admin",
					new TenantData("admin", "tenant_admin",
							new MongoClient()));
		}

		@Override
		public DB getDb() throws DataAccessException {
			String tenant = TenantContext.getCurrentTenant();
			TenantData tenantData = tenantDataMap.get(tenant);
			return MongoDbUtils.getDB(tenantData.client, tenantData.dbName);
		}

		@Override
		public DB getDb(String s) throws DataAccessException {
			return getDb();
		}

		private PersistenceExceptionTranslator exceptionTranslator
				= new MongoExceptionTranslator();

		@Override
		public PersistenceExceptionTranslator getExceptionTranslator() {
			return exceptionTranslator;
		}
	}

	@Bean
	public MongoDbFactory mongoDbFactory() {
		return new MySimpleMongoDbFactory();
	}
}
