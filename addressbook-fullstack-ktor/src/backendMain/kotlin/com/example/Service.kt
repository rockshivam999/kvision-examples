package com.example

import com.example.Db.dbQuery
import com.example.Db.queryList
import com.github.andrewoma.kwery.core.builder.query
import com.google.inject.Inject
import io.ktor.application.ApplicationCall
import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.exposed.sql.*
import org.joda.time.DateTime
import pl.treksoft.kvision.remote.Profile
import pl.treksoft.kvision.remote.withProfile
import java.sql.ResultSet

actual class AddressService : IAddressService {

    @Inject
    lateinit var call: ApplicationCall

    override suspend fun getAddressList(search: String?, types: String, sort: Sort) =
        call.withProfile { profile ->
            dbQuery {
                val query = query {
                    select("SELECT * FROM address")
                    whereGroup {
                        where("user_id = :user_id")
                        parameter("user_id", profile.id?.toInt())
                        search?.let {
                            where(
                                """(lower(first_name) like :search
                            OR lower(last_name) like :search
                            OR lower(email) like :search
                            OR lower(phone) like :search
                            OR lower(postal_address) like :search)""".trimMargin()
                            )
                            parameter("search", "%${it.toLowerCase()}%")
                        }
                        if (types == "fav") {
                            where("favourite")
                        }
                    }
                    when (sort) {
                        Sort.FN -> orderBy("lower(first_name)")
                        Sort.LN -> orderBy("lower(last_name)")
                        Sort.E -> orderBy("lower(email)")
                        Sort.F -> orderBy("favourite")
                    }
                }
                queryList(query.sql, query.parameters) {
                    toAddress(it)
                }
            }
        }

    override suspend fun addAddress(address: Address) = call.withProfile { profile ->
        val key = dbQuery {
            (AddressDao.insert {
                it[firstName] = address.firstName
                it[lastName] = address.lastName
                it[email] = address.email
                it[phone] = address.phone
                it[postalAddress] = address.postalAddress
                it[favourite] = address.favourite ?: false
                it[createdAt] = DateTime()
                it[userId] = profile.id?.toInt()!!

            } get AddressDao.id)
        }!!
        getAddress(key)!!
    }

    override suspend fun updateAddress(address: Address) = call.withProfile { profile ->
        address.id?.let {
            getAddress(it)?.let { oldAddress ->
                dbQuery {
                    AddressDao.update({ AddressDao.id eq it }) {
                        it[firstName] = address.firstName
                        it[lastName] = address.lastName
                        it[email] = address.email
                        it[phone] = address.phone
                        it[postalAddress] = address.postalAddress
                        it[favourite] = address.favourite ?: false
                        it[createdAt] = DateTime(oldAddress.createdAt)
                        it[userId] = profile.id?.toInt()!!
                    }
                }
            }
            getAddress(it)
        } ?: throw IllegalArgumentException("The ID of the address not set")
    }

    override suspend fun deleteAddress(id: Int): Boolean = call.withProfile { profile ->
        dbQuery {
            AddressDao.deleteWhere { (AddressDao.userId eq profile.id?.toInt()!!) and (AddressDao.id eq id) } > 0
        }
    }

    private suspend fun getAddress(id: Int): Address? = dbQuery {
        AddressDao.select {
            AddressDao.id eq id
        }.mapNotNull { toAddress(it) }.singleOrNull()
    }

    private fun toAddress(row: ResultRow): Address =
        Address(
            id = row[AddressDao.id],
            firstName = row[AddressDao.firstName],
            lastName = row[AddressDao.lastName],
            email = row[AddressDao.email],
            phone = row[AddressDao.phone],
            postalAddress = row[AddressDao.postalAddress],
            favourite = row[AddressDao.favourite],
            createdAt = row[AddressDao.createdAt]?.toDate(),
            userId = row[AddressDao.userId]
        )

    private fun toAddress(rs: ResultSet): Address =
        Address(
            id = rs.getInt(AddressDao.id.name),
            firstName = rs.getString(AddressDao.firstName.name),
            lastName = rs.getString(AddressDao.lastName.name),
            email = rs.getString(AddressDao.email.name),
            phone = rs.getString(AddressDao.phone.name),
            postalAddress = rs.getString(AddressDao.postalAddress.name),
            favourite = rs.getBoolean(AddressDao.favourite.name),
            createdAt = rs.getTimestamp(AddressDao.createdAt.name),
            userId = rs.getInt(AddressDao.userId.name)
        )
}

actual class ProfileService : IProfileService {

    @Inject
    lateinit var call: ApplicationCall

    override suspend fun getProfile() = call.withProfile { it }

}

actual class RegisterProfileService : IRegisterProfileService {

    override suspend fun registerProfile(profile: Profile, password: String): Boolean {
        try {
            dbQuery {
                UserDao.insert {
                    it[this.name] = profile.displayName!!
                    it[this.username] = profile.username!!
                    it[this.password] = DigestUtils.sha256Hex(password)
                }
            }
        } catch (e: Exception) {
            throw Exception("Register operation failed!")
        }
        return true
    }

}